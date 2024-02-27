//Copyright 2016 Radiologics, Inc
//Author: James Dickson <james@radiologics.com>
package com.radiologics.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.log4j.Logger;
import org.nrg.action.ClientException;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.model.XnatImageassessordataI;
import org.nrg.xdat.model.XnatImagescandataI;
import org.nrg.xdat.model.XnatReconstructedimagedataI;
import org.nrg.xdat.om.XnatAbstractresource;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatImageassessordata;
import org.nrg.xdat.om.XnatImagescandata;
import org.nrg.xdat.om.XnatImagesessiondata;
import org.nrg.xdat.om.XnatReconstructedimagedata;
import org.nrg.xdat.om.XnatSubjectassessordata;
import org.nrg.xdat.preferences.SiteConfigPreferences;
import org.nrg.xdat.security.XDATUser;
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
import org.nrg.xnat.turbine.utils.ArcSpecManager;
import org.nrg.xnat.utils.WorkflowUtils;
import org.restlet.data.Status;

/**
 * @author james@radiologics.com
 * 
 *  ResetSessionForScansReupload operation will delete the scans and change the status to quarantine. Session is now ready for additional data uploads.
 * 
 */
public class ResetSessionForScansOnlyReupload implements Callable<XnatExperimentdata>{
	public static Logger logger = Logger.getLogger(ResetSessionForCompleteReupload.class);
	final UserI user;
	XnatExperimentdata exp;
	final EventDetails details;
	private final SiteConfigPreferences siteConfig = XDAT.getContextService().getBeanSafely(SiteConfigPreferences.class);
	
	/**
	 * @param user for the operation
	 * @param exp experiment to be reset
	 * @param details for the transaction
	 */
	public ResetSessionForScansOnlyReupload(UserI user, XnatSubjectassessordata exp, EventDetails details){
		this.user=user;
		this.exp=exp;
		this.details=details;
	}

	
	@Override
	public XnatExperimentdata call() throws Exception {
		if(!exp.canEdit(user)){
			throw new InvalidPermissionException("User cannot modify this session");
		}
		
		if(PersistentWorkflowUtils.getOpenWorkflows(user, exp.getId()).size()>0){
			throw new ClientException(Status.CLIENT_ERROR_CONFLICT, "Experiment is locked for processing", new Exception());
		}
		
		PersistentWorkflowI wrk=PersistentWorkflowUtils.buildOpenWorkflow(user, exp.getItem(), details);
		wrk.setStatus(PersistentWorkflowUtils.IN_PROGRESS);
		PersistentWorkflowUtils.save(wrk, wrk.buildEvent());
		
		try {
			
			String rootPath=ArcSpecManager.GetInstance().getArchivePathForProject(exp.getProject());
	    	
			if(exp instanceof XnatImagesessiondata){
				//delete scans files
                String project = exp.getProject();
				for(final XnatImagescandataI scan: ((XnatImagesessiondata)exp).getScans_scan()){
		        	for(XnatAbstractresourceI abstRes:scan.getFile()){
		        		((XnatAbstractresource)abstRes).deleteWithBackup(rootPath, project, user, wrk.buildEvent());
		        	}
		    	}
				
			    //delete scans
				for(XnatImagescandataI scan: ((XnatImagesessiondata)exp).getScans_scan()){
					DBAction.DeleteItem(((XnatImagescandata)scan).getItem().getCurrentDBVersion(), user,wrk.buildEvent());
				}
			}
			XnatExperimentdata newCopy =exp.getLightCopy();
			
			newCopy.getItem().setStatus(user, ViewManager.QUARANTINE);
			if(newCopy instanceof XnatImagesessiondata){
				newCopy.getItem().setProperty("UID", "NULL");
				newCopy.getItem().setProperty("dcmPatientId", "NULL");
				newCopy.getItem().setProperty("dcmPatientName", "NULL");
				newCopy.getItem().setProperty("dcmPatientBirthDate", "NULL");
			}
			
	    	SaveItemHelper.authorizedSave(newCopy, user, false, false, wrk.buildEvent());//passing allowDataDeletion=true means anything missing is deleted
	    	
			PersistentWorkflowUtils.complete(wrk, wrk.buildEvent());
		} catch (Exception e) {
			logger.error("",e);
			try{if(wrk!=null)PersistentWorkflowUtils.fail(wrk, wrk.buildEvent());}catch(Exception e1){}
			throw e;
		}

		if(BooleanUtils.toBooleanDefaultIfNull(siteConfig.getBooleanValue("esignSendScansOnlyReuploadEmail"), false)){
			this.sendEmail();
		}
		return exp;
	}

	public void sendEmail() throws Exception{
		List<String> recipients = null;
		if(BooleanUtils.toBooleanDefaultIfNull(siteConfig.getBooleanValue("esignUseProjectNotificationLists"), false)) {
			NotifyProjectListeners.ProjectListenersI listener = new NotifyProjectListeners.ResourceBasedProjectListeners();
			recipients = listener.call("session_modified.lst", exp.getProjectData(), exp);
		}

		final String subjectTemplate = (String) siteConfig.getProperty("esignScansOnlyReuploadEmailSubject", EsignEmailHelper.DEFAULT_SCANS_ONLY_REUPLOAD_SUBJECT);
		final String bodyTemplate    = (String) siteConfig.getProperty("esignScansOnlyReuploadEmailBody", EsignEmailHelper.DEFAULT_SCANS_ONLY_REUPLOAD_EMAIL);
		EsignEmailHelper.sendEmail(exp, user, subjectTemplate, bodyTemplate, recipients);
	}
}
