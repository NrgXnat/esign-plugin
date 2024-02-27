//Copyright 2012 Radiologics, Inc
//Author: Tim Olsen <tim@radiologics.com>
package com.radiologics.utils;

import java.util.concurrent.Callable;

import org.nrg.xdat.base.BaseElement;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xft.XFTItem;
import org.nrg.xft.db.ViewManager;
import org.nrg.xft.event.EventDetails;
import org.nrg.xft.security.UserI;
import org.nrg.xft.utils.SaveItemHelper;

/**
 * @author james@radiologics.com
 *
 * Versioning implementation that will rollback an experiment.
 *
 */
public class EsigRollback implements Callable<XnatExperimentdata>{
	final UserI user;
	XnatExperimentdata exp;
	XnatExperimentdata target;
	final EventDetails details;
	
	/**
	 * @param user peforming action
	 * @param exp experiment to be versioned
	 * @param details event details for transaction
	 */
	public EsigRollback(UserI user, XnatExperimentdata exp,XnatExperimentdata target, EventDetails details){
		this.user=user;
		this.exp=exp;
		this.target=target;
		this.details=details;
	}
	
	@Override
	public XnatExperimentdata call() throws Exception {

		SessionRollbackImpl srollback = new SessionRollbackImpl(exp,target, user,details);
		
		XnatExperimentdata rollbackEXPT = srollback.call();
	
		//refresh object
		XFTItem i=rollbackEXPT.getCurrentDBVersion();
		rollbackEXPT=(XnatExperimentdata)BaseElement.GetGeneratedItem(i);
		
		
		
				
		rollbackEXPT.setVersion(target.getVersion()+1);
		
		
    	SaveItemHelper.authorizedSave(rollbackEXPT.getItem(), user, false, false, details);
        
		return rollbackEXPT;
	}
	
}