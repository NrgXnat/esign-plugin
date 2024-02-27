//Copyright 2012 Radiologics, Inc
//Author: Tim Olsen <tim@radiologics.com>
package org.nrg.xnat.restlet.extensions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.base.auto.AutoXnatExperimentdata;
import org.nrg.xft.event.EventUtils;
import org.nrg.xft.event.persist.PersistentWorkflowUtils;
import org.nrg.xnat.notifications.NotifyProjectListeners;
import org.nrg.xnat.restlet.XnatRestlet;
import org.nrg.xnat.restlet.resources.SecureResource;
import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.Representation;
import org.restlet.resource.StringRepresentation;
import org.restlet.resource.Variant;

import com.google.common.collect.Maps;
import com.radiologics.utils.MakeObsolete;

/**
 * @author tim@radiologics.com
 *
 * Implementation of the Obsoletion feature.  POST method will obsolete the data object and add a workflow entry.
 */
@XnatRestlet("/services/esig/obsolete")
public class Obsolete extends SecureResource {
	private static final long serialVersionUID = 8092462697472805786L;
	XnatExperimentdata exp = null;
	String exptID = null;
	String filepath = null;

	/**
	 * @param context standard
	 * @param request standard
	 * @param response standard
	 */
	public Obsolete(Context context, Request request, Response response) {
		super(context, request, response);
		
		exptID = (String) getQueryVariable("EXPT_ID");

		exp = AutoXnatExperimentdata.getXnatExperimentdatasById(exptID,this.getUser(), false);
		
		if(exp!=null){
	        this.getVariants().add(new Variant(MediaType.ALL));
		}
	}

	@Override
	public boolean allowPost() {
		return true;
	}

	@Override
	public void handlePost() {
		try {
			if(!exp.canEdit(this.getUser())){
				this.getResponse().setStatus(Status.CLIENT_ERROR_FORBIDDEN);
				return;
			}
		} catch (Exception e1) {
			logger.error("",e1);
			this.getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
			return;
		}
				
		Map<String,Object> params=Maps.newHashMap();
		params.put("justification", this.getReason());
		
		if(PersistentWorkflowUtils.getOpenWorkflows(this.getUser(), exp.getId()).size()>0){
			this.getResponse().setStatus(Status.CLIENT_ERROR_CONFLICT);
			return;
		}
		
		try {			
			//mark session as obsolete
			(new MakeObsolete(this.getUser(), exp, newEventInstance(EventUtils.CATEGORY.DATA, "Deleted"))).call();

			try {
				(new NotifyProjectListeners(exp, "/screens/email/obsolete_expt_suc.vm", "deleted.", this.getUser(), new HashMap<String,Object>(),"deletion.lst",new ArrayList<String>())).call();
			} catch (Exception e1) {
				logger.error("",e1);
			}
			
			if (exp != null){
				Representation representation = new StringRepresentation(exp.getId());
				this.getResponse().setEntity(representation);
				this.getResponse().setStatus(Status.SUCCESS_OK);
			}
		} catch (Exception e) {
			logger.error("",e);
			this.getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
			
			return;
		}
	}
	
	
}
