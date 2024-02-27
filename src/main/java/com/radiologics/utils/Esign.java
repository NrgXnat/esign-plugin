//Copyright 2012 Radiologics, Inc
//Author: Tim Olsen <tim@radiologics.com>
package com.radiologics.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.log4j.Logger;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.preferences.SiteConfigPreferences;
import org.nrg.xft.db.ViewManager;
import org.nrg.xft.event.EventDetails;
import org.nrg.xft.event.persist.PersistentWorkflowI;
import org.nrg.xft.event.persist.PersistentWorkflowUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xft.utils.SaveItemHelper;
import org.nrg.xnat.notifications.NotifyProjectListeners;

/**
 * @author tim@radiologics.com
 *
 * Esign implementation 
 */
public class Esign implements Callable<XnatExperimentdata>{

	public static Logger logger = Logger.getLogger(Esign.class);
	final UserI user;
	final XnatExperimentdata exp;
	final EventDetails details;
	private final SiteConfigPreferences siteConfig = XDAT.getContextService().getBeanSafely(SiteConfigPreferences.class);
	
	/**
	 * @param user user for event
	 * @param exp experiment for event
	 * @param details event details
	 */
	public Esign(UserI user, XnatExperimentdata exp, EventDetails details){
		this.user=user;
		this.exp=exp;
		this.details=details;
	}
	
	/* (non-Javadoc)
	 * @see java.util.concurrent.Callable#call()
	 */
	@Override
	public XnatExperimentdata call() throws Exception {
		
		PersistentWorkflowI wrk = null;
		try {
			wrk=PersistentWorkflowUtils.buildOpenWorkflow(user, exp.getItem(), details);
			wrk.setStatus(PersistentWorkflowUtils.IN_PROGRESS);
			PersistentWorkflowUtils.save(wrk, wrk.buildEvent());
			
			//set version
			if(exp.getVersion()==null){
				exp.setVersion(new Integer(1));
				
				SaveItemHelper.authorizedSave(exp.getItem(), user, false, false, wrk.buildEvent());
			}
			
			exp.getItem().setStatus(user, ViewManager.LOCKED);
			PersistentWorkflowUtils.complete(wrk, wrk.buildEvent());
		} catch (Exception e) {
			logger.error("",e);
			try{
				if(wrk!=null){
					PersistentWorkflowUtils.fail(wrk, wrk.buildEvent());
				}
			}catch(Exception e1){
				//ignore
			}
			
			throw e;
		}

		if(BooleanUtils.toBooleanDefaultIfNull(siteConfig.getBooleanValue("esignSendLockedEmail"), true)){
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

		final String subjectTemplate = (String) siteConfig.getProperty("esignLockedEmailSubject", EsignEmailHelper.DEFAULT_LOCKED_SUBJECT);
		final String bodyTemplate    = (String) siteConfig.getProperty("esignLockedEmailBody", EsignEmailHelper.DEFAULT_LOCKED_EMAIL);
		EsignEmailHelper.sendEmail(exp, user, subjectTemplate, bodyTemplate, recipients);
	}
}
