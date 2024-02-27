//Copyright 2016 Radiologics, Inc
//Author: James Dickson <james@radiologics.com>
package com.radiologics.utils;

import java.util.concurrent.Callable;
import org.apache.log4j.Logger;
import org.nrg.action.ClientException;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xft.db.ViewManager;
import org.nrg.xft.event.EventDetails;
import org.nrg.xft.event.persist.PersistentWorkflowI;
import org.nrg.xft.event.persist.PersistentWorkflowUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xft.utils.SaveItemHelper;
import org.restlet.data.Status;

/**
 * @author james@radiologics.com
 * 
 * Modifies the existing experiment so that it is made active.
 * 
 */
public class MakeActive implements Callable<XnatExperimentdata>{
	public static Logger logger = Logger.getLogger(MakeActive.class);
	final UserI user;
	final XnatExperimentdata exp;
	final EventDetails details;
	
	/**
	 * @param user for operation
	 * @param exp experiment to be 
	 * @param details event details
	 */
	public MakeActive(UserI user, XnatExperimentdata exp, EventDetails details){
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
			
			//set version
			if(exp.getVersion()==null){
				exp.setVersion(new Integer(1));
				
				SaveItemHelper.authorizedSave(exp.getItem(), user, false, false, wrk.buildEvent());
			}
			
			exp.getItem().setStatus(user, ViewManager.ACTIVE);
			
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
		
		return exp;
	}
}
