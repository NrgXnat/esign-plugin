//Copyright 2016 Radiologics, Inc
//Author: James Dickson <james@radiologics.com>
package com.radiologics.utils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.log4j.Logger;
import org.nrg.action.ClientException;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.model.XnatImageassessordataI;
import org.nrg.xdat.model.XnatImagescandataI;
import org.nrg.xdat.model.XnatReconstructedimagedataI;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatImageassessordata;
import org.nrg.xdat.om.XnatImagescandata;
import org.nrg.xdat.om.XnatImagesessiondata;
import org.nrg.xdat.om.XnatReconstructedimagedata;
import org.nrg.xdat.om.XnatSubjectassessordata;
import org.nrg.xdat.preferences.SiteConfigPreferences;
import org.nrg.xft.XFTItem;
import org.nrg.xft.db.DBAction;
import org.nrg.xft.db.ViewManager;
import org.nrg.xft.event.EventDetails;
import org.nrg.xft.event.persist.PersistentWorkflowI;
import org.nrg.xft.event.persist.PersistentWorkflowUtils;
import org.nrg.xft.exception.InvalidPermissionException;
import org.nrg.xft.security.UserI;
import org.nrg.xft.utils.SaveItemHelper;
import org.nrg.xnat.notifications.NotifyProjectListeners;
import org.restlet.data.Status;

/**
 * @author james@radiologics.com
 *
 *  ResetSessionForCompleteReuploadAndRename operation will remove all content,change the status to obsolete and rename session label. Session remains to keep the audit trail.
 *
 */
public class ResetSessionForCompleteReuploadAndRename  implements Callable<XnatExperimentdata>{
	public static Logger logger = Logger.getLogger(ResetSessionForCompleteReuploadAndRename.class);
	final UserI user;
	XnatExperimentdata exp;
	final EventDetails details;
	private final SiteConfigPreferences siteConfig = XDAT.getContextService().getBeanSafely(SiteConfigPreferences.class);
	
    private static final String TIMESTAMP = "yyyyMMdd_HHmmss";

	/**
	 * @param user for the operation
	 * @param exp experiment to be reset
	 * @param details for the transaction
	 */
	public ResetSessionForCompleteReuploadAndRename(UserI user, XnatSubjectassessordata exp, EventDetails details){
		this.user=user;
		this.exp=exp;
		this.details=details;
	}

	
	@Override
	public XnatExperimentdata call() throws Exception {
		if(!exp.canEdit(user)){
			throw new InvalidPermissionException("User cannot modify this session");
		}

		if(exp instanceof XnatImagesessiondata){
			for (XnatImageassessordataI assess: ((XnatImagesessiondata)exp).getAssessors_assessor()){
				if(!((XnatImageassessordata)assess).canEdit(user)){
					throw new InvalidPermissionException("User cannot modify all assessments for this session");
				}
			}
		}
		
		if(PersistentWorkflowUtils.getOpenWorkflows(user, exp.getId()).size()>0){
			throw new ClientException(Status.CLIENT_ERROR_CONFLICT, "Experiment is locked for processing", new Exception());
		}
		
		PersistentWorkflowI wrk=PersistentWorkflowUtils.buildOpenWorkflow(user, exp.getItem(), details);
		wrk.setStatus(PersistentWorkflowUtils.IN_PROGRESS);
		PersistentWorkflowUtils.save(wrk, wrk.buildEvent());
		
		try {
			
			//first delete the files for this experiment
			exp.deleteFiles(user,wrk.buildEvent());

			if(exp instanceof XnatImagesessiondata){
			    
			    //delete scans
				for(XnatImagescandataI scan: ((XnatImagesessiondata)exp).getScans_scan()){
					DBAction.DeleteItem(((XnatImagescandata)scan).getItem().getCurrentDBVersion(), user,wrk.buildEvent());
				}
				
				//delete reconstructions
				for (XnatReconstructedimagedataI recon: ((XnatImagesessiondata)exp).getReconstructions_reconstructedimage()){
					DBAction.DeleteItem(((XnatReconstructedimagedata)recon).getItem().getCurrentDBVersion(), user,wrk.buildEvent());
				}
				
				//delete assessments
				for (XnatImageassessordataI assess: ((XnatImagesessiondata)exp).getAssessors_assessor()){
					DBAction.DeleteItem(((XnatImageassessordata)assess).getItem().getCurrentDBVersion(), null,wrk.buildEvent());
				}
			}
			
			final DateFormat dateFormat = new SimpleDateFormat(TIMESTAMP);
	        final Date date = new Date();
	        String dateAppend = dateFormat.format(date);
			//reset experiment to just the id, label, and project
			XFTItem item = XFTItem.NewItem(exp.getXSIType(), user);
			item.setProperty("ID", exp.getId());
			item.setProperty("project", exp.getProject());
			item.setProperty("label", exp.getLabel()+"_"+dateAppend);
			item.setProperty("visit_ID", exp.getVisitId());
			if(exp instanceof XnatSubjectassessordata){
				item.setProperty("subject_ID", ((XnatSubjectassessordata)exp).getSubjectId());
			}else if(exp instanceof XnatImageassessordata){
				item.setProperty("imageSession_ID", ((XnatImageassessordata)exp).getImagesessionId());
			}
			
			
	    	SaveItemHelper.authorizedSave(item, user, false, true, wrk.buildEvent());//passing allowDataDeletion=true means anything missing is deleted
			
	    	exp.getItem().setStatus(user, ViewManager.OBSOLETE);

			PersistentWorkflowUtils.complete(wrk, wrk.buildEvent());
		} catch (Exception e) {
			logger.error("",e);
			try{if(wrk!=null)PersistentWorkflowUtils.fail(wrk, wrk.buildEvent());}catch(Exception e1){}
			throw e;
		}

		if(BooleanUtils.toBooleanDefaultIfNull(siteConfig.getBooleanValue("esignSendCompleteRenameReuploadEmail"), false)){
			this.sendEmail();
		}
		return exp;
	}

	public void sendEmail() throws Exception{

		List<String> recipients = null;
		if(BooleanUtils.toBooleanDefaultIfNull(siteConfig.getBooleanValue("esignUseProjectNotificationLists"), false)) {
			NotifyProjectListeners.ProjectListenersI listener = new NotifyProjectListeners.ResourceBasedProjectListeners();
			recipients = listener.call("deletion.lst", exp.getProjectData(), exp);
		}

		final String subjectTemplate = (String) siteConfig.getProperty("esignCompleteRenameReuploadEmailSubject", EsignEmailHelper.DEFAULT_COMPLETE_RENAME_REUPLOAD_SUBJECT);
		final String bodyTemplate    = (String) siteConfig.getProperty("esignCompleteRenameReuploadEmailBody", EsignEmailHelper.DEFAULT_COMPLETE_RENAME_REUPLOAD_EMAIL);
		EsignEmailHelper.sendEmail(exp, user, subjectTemplate, bodyTemplate, recipients);
	}
}