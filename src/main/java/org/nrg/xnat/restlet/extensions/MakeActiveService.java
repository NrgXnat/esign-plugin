//Copyright 2016 Radiologics, Inc
//Author: James Dickson <james@radiologics.com>
package org.nrg.xnat.restlet.extensions;

import java.util.ArrayList;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatSubjectassessordata;
import org.nrg.xdat.om.base.auto.AutoXnatExperimentdata;
import com.radiologics.utils.MakeActive;

import org.nrg.xft.db.ViewManager;
import org.nrg.xft.event.EventUtils;
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
import com.radiologics.utils.ResetSessionForCompleteReupload;
import com.radiologics.utils.ResetSession;


/**
 * @author james@radiologics.com	
 *
 * Make a session active. 
 */
@XnatRestlet("/services/esig/active")
public class MakeActiveService extends SecureResource {
	private static final long serialVersionUID = 8092464697472805786L;
	XnatExperimentdata exp = null;
	String exptID = null;
	String filepath = null;

	/**
	 * @param context standard
	 * @param request standard
	 * @param response standard
	 */
	public MakeActiveService(Context context, Request request, Response response) {
		super(context, request, response);
		
		exptID = (String) getQueryVariable("EXPT_ID");

		exp = AutoXnatExperimentdata.getXnatExperimentdatasById(exptID,
				this.getUser(), false);
		
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
			if(StringUtils.equals(exp.getItem().getStatus(), ViewManager.LOCKED)){
				this.getResponse().setStatus(Status.CLIENT_ERROR_FORBIDDEN);
				return;
			}
			if(StringUtils.equals(exp.getItem().getStatus(), ViewManager.OBSOLETE)){
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
		
		try {
			(new MakeActive(this.getUser(), (XnatSubjectassessordata) exp, this.newEventInstance(EventUtils.CATEGORY.DATA,"Make Session Active"))).call();

			try {
				(new NotifyProjectListeners(exp, "/screens/email/reset_expt_suc.vm", "reset", this.getUser(), params,"reset.lst",new ArrayList<String>())).call();
			} catch (Exception e1) {
				logger.error("",e1);
			}
			
			Representation representation = new StringRepresentation(exp.getId());
			this.getResponse().setEntity(representation);
			this.getResponse().setStatus(Status.SUCCESS_OK);
		} catch (Exception e) {
			logger.error("",e);
			this.getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
			
			return;
		}
	}
	
	
}