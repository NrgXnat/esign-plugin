//Copyright 2012 Radiologics, Inc
//Author: Tim Olsen <tim@radiologics.com>
package com.radiologics.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.log4j.Logger;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.base.BaseElement;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.preferences.SiteConfigPreferences;
import org.nrg.xft.XFTItem;
import org.nrg.xft.db.ViewManager;
import org.nrg.xft.event.EventDetails;
import org.nrg.xft.security.UserI;
import org.nrg.xft.utils.SaveItemHelper;
import org.nrg.xnat.notifications.NotifyProjectListeners;

/**
 * @author tim@radiologics.com
 *
 * Versioning implementation that unlocks an experiment and creates a full copy of it.
 *
 */
public class EsigVersion implements Callable<XnatExperimentdata>{

	public static Logger logger = Logger.getLogger(EsigVersion.class);
	final UserI user;
	XnatExperimentdata exp;
	final EventDetails details;

	private final SiteConfigPreferences siteConfig = XDAT.getContextService().getBeanSafely(SiteConfigPreferences.class);
	
	/**
	 * @param user peforming action
	 * @param exp experiment to be versioned
	 * @param details event details for transaction
	 */
	public EsigVersion(UserI user, XnatExperimentdata exp, EventDetails details){
		this.user=user;
		this.exp=exp;
		this.details=details;
	}
	
	@Override
	public XnatExperimentdata call() throws Exception {

		SessionCopyImpl scopy = new SessionCopyImpl(exp, user,details);
		
		XnatExperimentdata newEXPT = scopy.call();

		// Are we unlocking the session? (i.e. not rolling back an active session)
		final Boolean unlocking = ViewManager.LOCKED == exp.getItem().getStatus();

		//mark copied session as obsolete
		newEXPT.getItem().setStatus(user, ViewManager.OBSOLETE);

		//make original accessible
		exp.getItem().setStatus(user, ViewManager.ACTIVE);

		//refresh object
		XFTItem i=exp.getCurrentDBVersion();
		exp=(XnatExperimentdata)BaseElement.GetGeneratedItem(i);

		exp.setVersion(newEXPT.getVersion()+1);

    	SaveItemHelper.authorizedSave(exp.getItem(), user, false, false, details);

    	// If we unlocked the session, send an email
		if(unlocking && BooleanUtils.toBooleanDefaultIfNull(siteConfig.getBooleanValue("esignSendUnlockedEmail"), false)){
			this.sendUnlockedEmail();
		}
		return newEXPT;
	}

	public void sendUnlockedEmail() throws Exception{
		List<String> recipients = null;
		if(BooleanUtils.toBooleanDefaultIfNull(siteConfig.getBooleanValue("esignUseProjectNotificationLists"), false)) {
			NotifyProjectListeners.ProjectListenersI listener = new NotifyProjectListeners.ResourceBasedProjectListeners();
			recipients = listener.call("session_modified.lst", exp.getProjectData(), exp);
		}

		final String subjectTemplate = (String) siteConfig.getProperty("esignUnLockedEmailSubject", EsignEmailHelper.DEFAULT_UNLOCKED_SUBJECT);
		final String bodyTemplate    = (String) siteConfig.getProperty("esignUnLockedEmailBody", EsignEmailHelper.DEFAULT_UNLOCKED_EMAIL);
		EsignEmailHelper.sendEmail(exp, user, subjectTemplate, bodyTemplate, recipients);
	}
}