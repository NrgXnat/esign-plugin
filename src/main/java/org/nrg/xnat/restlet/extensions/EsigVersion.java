//Copyright 2012 Radiologics, Inc
//Author: Tim Olsen <tim@radiologics.com>
package org.nrg.xnat.restlet.extensions;

import java.util.Collection;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.nrg.xdat.om.WrkWorkflowdata;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.base.auto.AutoXnatExperimentdata;
import org.nrg.xft.event.EventUtils;
import org.nrg.xft.event.persist.PersistentWorkflowI;
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
import com.google.common.collect.Maps;


/**
 * @author tim@radiologics.com
 *
 * Implementation of the Create new version feature.  POST method will copy the data object, add a workflow entry to the new one and old one, increment the version of the old one and unlock it.
 */
@XnatRestlet("/services/esig/version")
public class EsigVersion extends SecureResource {

	/**
	 * 
	 */
	private static final long serialVersionUID = 8092462697472805786L;
	XnatExperimentdata exp = null;
	String exptID = null;
	String filepath = null;
	String override = null;

	/**
	 * @param context standard
	 * @param request standard
	 * @param response standard
	 */
	public EsigVersion(Context context, Request request, Response response) {
		super(context, request, response);
		
		exptID = (String) getQueryVariable("EXPT_ID");

		override = (String) getQueryVariable("override");
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
		} catch (Exception e1) {
			logger.error("",e1);
			this.getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
			return;
		}
				
		Map<String,Object> params=Maps.newHashMap();
		params.put("justification", this.getReason());
		
		
		if(PersistentWorkflowUtils.getOpenWorkflows(this.getUser(), exp.getId()).size()>0 && !BooleanUtils.toBoolean(override)){
			this.getResponse().setStatus(Status.CLIENT_ERROR_CONFLICT);
			return;
		}
		
		try{			
			XnatExperimentdata newEXPT=(new com.radiologics.utils.EsigVersion(this.getUser(), exp, this.newEventInstance(EventUtils.CATEGORY.DATA,"Created New Version"))).call();
		
			if (newEXPT != null){
				Representation representation = new StringRepresentation(newEXPT.getId());
				this.getResponse().setEntity(representation);
				this.getResponse().setStatus(Status.SUCCESS_CREATED);
			}
		} catch (Exception e) {
			logger.error("",e);
			this.getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
	
			return;
		}
	}
}
