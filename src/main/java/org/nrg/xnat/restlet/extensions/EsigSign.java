//Copyright 2012 Radiologics, Inc
//Author: Tim Olsen <tim@radiologics.com>
package org.nrg.xnat.restlet.extensions;

import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.base.auto.AutoXnatExperimentdata;
import org.nrg.xft.event.EventUtils;
import org.nrg.xft.event.persist.PersistentWorkflowUtils;
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

import com.radiologics.utils.Esign;

/**
 * @author tim@radiologics.com	
 *
 * Implementation of the esign functionality.  The post method adds a workflow entry, increments the version, and locks the data object.
 */
@XnatRestlet("/services/esig/sign")
public class EsigSign extends SecureResource {

	private static final long serialVersionUID = 8092462697472805786L;
	XnatExperimentdata exp = null;
	String exptID = null;
	String filepath = null;

	/**
	 * @param context standard
	 * @param request standard
	 * @param response standard
	 */
	public EsigSign(Context context, Request request, Response response) {
		super(context, request, response);
		
		exptID = (String) getQueryVariable("EXPT_ID");

		exp = AutoXnatExperimentdata.getXnatExperimentdatasById(exptID, this.getUser(), false);
		
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
			
			if(!exp.getItem().isActive() || exp.getItem().isLocked()){
				this.getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
				return;
			}
		} catch (Exception e1) {
			logger.error("",e1);
			this.getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
			return;
		}
		
		if(PersistentWorkflowUtils.getOpenWorkflows(this.getUser(), exp.getId()).size()>0){
			this.getResponse().setStatus(Status.CLIENT_ERROR_CONFLICT);
			return;
		}
				
		try {
			//sign the experiment
			(new Esign(this.getUser(), exp, this.newEventInstance(EventUtils.CATEGORY.DATA,"E-signed"))).call();

			Representation representation = new StringRepresentation(exp.getId());
			this.getResponse().setEntity(representation);
			this.getResponse().setStatus(Status.SUCCESS_ACCEPTED);
		} catch (Exception e) {
			logger.error("",e);
			this.getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
			return;
		}
	}
	
	
}
