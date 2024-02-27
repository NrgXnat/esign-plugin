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
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.preferences.SiteConfigPreferences;
import org.nrg.xft.db.ViewManager;
import org.nrg.xft.event.EventDetails;
import org.nrg.xft.event.persist.PersistentWorkflowI;
import org.nrg.xft.event.persist.PersistentWorkflowUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.notifications.NotifyProjectListeners;
import org.restlet.data.Status;

/**
 * @author james@radiologics.com
 * 
 *  ResetSessionForPartialReupload operation will change the status to quarantine and leave the files. Session is now ready for additional data uploads.
 * 
 */
public class ResetSessionForPartialReupload implements Callable<XnatExperimentdata>{
	public static Logger logger = Logger.getLogger(ResetSessionForPartialReupload.class);
	final UserI user;
	final XnatExperimentdata exp;
	final EventDetails details;

	private final SiteConfigPreferences siteConfig = XDAT.getContextService().getBeanSafely(SiteConfigPreferences.class);
	
	/**
	 * @param user for operation
	 * @param exp experiment to be 
	 * @param details event details
	 */
	public ResetSessionForPartialReupload(UserI user, XnatExperimentdata exp, EventDetails details){
		this.user=user;
		this.exp=exp;
		this.details=details;
	}

	
	@Override
	public XnatExperimentdata call() throws Exception {
		
		PersistentWorkflowI wrk = null;
		try {
			if(PersistentWorkflowUtils.getOpenWorkflows(user, exp.getId()).size()>0){
				throw new ClientException(Status.CLIENT_ERROR_CONFLICT, "Experiment is locked for processing", new Exception());
			}
			
			wrk=PersistentWorkflowUtils.buildOpenWorkflow(user, exp.getItem(), details);
			
			wrk.setStatus(PersistentWorkflowUtils.IN_PROGRESS);
			PersistentWorkflowUtils.save(wrk,wrk.buildEvent());
	
			exp.getItem().setStatus(user, ViewManager.QUARANTINE);
			
			PersistentWorkflowUtils.complete(wrk, wrk.buildEvent());
		} catch (Exception e) {
			logger.error("",e);
			try{
				if(wrk!=null){
					PersistentWorkflowUtils.fail(wrk, wrk.buildEvent());
				}
			}catch(Exception e1){}
			throw e;
		}

		if(BooleanUtils.toBooleanDefaultIfNull(siteConfig.getBooleanValue("esignSendPartialReuploadEmail"), false)){
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

		final String subjectTemplate = (String) siteConfig.getProperty("esignPartialReuploadEmailSubject", EsignEmailHelper.DEFAULT_PARTIAL_REUPLOAD_SUBJECT);
		final String bodyTemplate    = (String) siteConfig.getProperty("esignPartialReuploadEmailBody", EsignEmailHelper.DEFAULT_PARTIAL_REUPLOAD_EMAIL);
		EsignEmailHelper.sendEmail(exp, user, subjectTemplate, bodyTemplate, recipients);
	}
}
