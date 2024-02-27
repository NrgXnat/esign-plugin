//Copyright 2012 Radiologics, Inc
//Author: Tim Olsen <tim@radiologics.com>
package com.radiologics.utils;

import java.util.concurrent.Callable;

import org.apache.log4j.Logger;
import org.nrg.action.ClientException;
import org.nrg.xdat.base.BaseElement;
import org.nrg.xdat.model.XnatImageassessordataI;
import org.nrg.xdat.model.XnatImagescandataI;
import org.nrg.xdat.model.XnatReconstructedimagedataI;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatImageassessordata;
import org.nrg.xdat.om.XnatImagescandata;
import org.nrg.xdat.om.XnatImagesessiondata;
import org.nrg.xdat.om.XnatReconstructedimagedata;
import org.nrg.xdat.om.XnatSubjectassessordata;
import org.nrg.xdat.security.helpers.Permissions;
import org.nrg.xft.XFTItem;
import org.nrg.xft.db.DBAction;
import org.nrg.xft.event.EventDetails;
import org.nrg.xft.event.EventUtils;
import org.nrg.xft.event.persist.PersistentWorkflowI;
import org.nrg.xft.event.persist.PersistentWorkflowUtils;
import org.nrg.xft.exception.InvalidPermissionException;
import org.nrg.xft.security.UserI;
import org.nrg.xft.utils.SaveItemHelper;
import org.restlet.data.Status;

/**
 * @author tim@radiologics.com
 *
 * Session reset operation will create a full copy of the experiment and then delete everything from the session.
 *
 */
public class ResetSession  implements Callable<XnatExperimentdata>{
	public static Logger logger = Logger.getLogger(ResetSession.class);
	final UserI user;
	XnatExperimentdata exp;
	final EventDetails details;
	
	
	/**
	 * @param user for the operation
	 * @param exp experiment to be reset
	 * @param details for the transaction
	 */
	public ResetSession(UserI user, XnatSubjectassessordata exp, EventDetails details){
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
				if(!Permissions.canEdit(user, (XnatImageassessordata)assess)){
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
			
			(new com.radiologics.utils.EsigVersion(user, exp, EventUtils.newEventInstance(EventUtils.CATEGORY.DATA,EventUtils.TYPE.WEB_SERVICE,"Created New Version",(details.getReason()),null))).call();
			
			//refresh exp after versioning
			exp=(XnatExperimentdata)BaseElement.GetGeneratedItem(exp.getCurrentDBVersion());
			
			
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
			
			//reset experiment to just the id, label, and project
			XFTItem item = XFTItem.NewItem(exp.getXSIType(), user);
			item.setProperty("ID", exp.getId());
			item.setProperty("project", exp.getProject());
			item.setProperty("label", exp.getLabel());
			item.setProperty("visit_ID", exp.getVisitId());
			item.setProperty("version",exp.getVersion());
			if(exp instanceof XnatSubjectassessordata){
				item.setProperty("subject_ID", ((XnatSubjectassessordata)exp).getSubjectId());
			}else if(exp instanceof XnatImageassessordata){
				item.setProperty("imageSession_ID", ((XnatImageassessordata)exp).getImagesessionId());
			}
			
	    	SaveItemHelper.authorizedSave(item, user, false, true, wrk.buildEvent());//passing allowDataDeletion=true means anything missing is deleted
			
			PersistentWorkflowUtils.complete(wrk, wrk.buildEvent());
		} catch (Exception e) {
			logger.error("",e);
			try{if(wrk!=null)PersistentWorkflowUtils.fail(wrk, wrk.buildEvent());}catch(Exception e1){}
			throw e;
		}
		
		return exp;
	}
}