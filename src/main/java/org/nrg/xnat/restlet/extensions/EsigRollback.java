//Copyright 2012 Radiologics, Inc
//Author: Tim Olsen <tim@radiologics.com>
package org.nrg.xnat.restlet.extensions;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ecs.xhtml.pre;
import org.nrg.action.ClientException;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatSubjectassessordata;
import org.nrg.xdat.om.base.auto.AutoXnatExperimentdata;
import org.nrg.xdat.security.helpers.UserHelper;
import org.nrg.xft.event.EventUtils;
import org.nrg.xft.event.persist.PersistentWorkflowUtils;
import org.nrg.xft.exception.DBPoolException;
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
@XnatRestlet("/services/esig/rollback")
public class EsigRollback extends SecureResource {

	/**
	 * 
	 */
	private static final long serialVersionUID = 8092462697472805786L;
	XnatExperimentdata exp = null;
	String exptID = null;
	String filepath = null;
    String override = null;
    String version=null;

	/**
	 * @param context standard
	 * @param request standard
	 * @param response standard
	 */
	public EsigRollback(Context context, Request request, Response response) {
		super(context, request, response);
		
		exptID = (String) getQueryVariable("EXPT_ID");
		override = (String) getQueryVariable("override");
		version = (String) getQueryVariable("version");
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
			XnatExperimentdata prevVersion=this.getVersion(exp);
			if(prevVersion==null){
				this.getResponse().setStatus(Status.CLIENT_ERROR_CONFLICT);
				return;
			}
			(new com.radiologics.utils.ResetSession(this.getUser(), (XnatSubjectassessordata)exp, this.newEventInstance(EventUtils.CATEGORY.DATA,"Reset Session"))).call();
			//retrieve the rollback version.
			
			(new com.radiologics.utils.EsigRollback(this.getUser(), prevVersion,exp, this.newEventInstance(EventUtils.CATEGORY.DATA,"Rollback Session"))).call();

			Representation representation = new StringRepresentation(exp.getId());
			this.getResponse().setEntity(representation);
			this.getResponse().setStatus(Status.SUCCESS_OK);
			
		} catch (Exception e) {
			logger.error("",e);
			this.getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
	
			return;
		}
	}
	
	
	XnatExperimentdata getVersion(XnatExperimentdata exp) throws SQLException, DBPoolException{
		String sql="SELECT ID,label,version,status,insert_date,original FROM xnat_experimentData expt LEFT JOIN xnat_experimentData_meta_data meta ON expt.experimentData_info=meta.meta_data_id WHERE (original='"+exp.getId()+"' OR id='"+exp.getId()+"') OR (original='"+exp.getOriginal()+"' OR id='"+exp.getOriginal()+"') ORDER BY version DESC";
		List<List> versions=UserHelper.getUserHelperService(this.getUser()).getQueryResultsAsArrayList(sql);
		for (List list : versions) {
			String id =(String)list.get(0);
			String label =(String)list.get(1);
			//this is silly..
			String versionString="";
			try{
				Integer version=(Integer)list.get(2);
				versionString=Integer.toString(version);
			}catch(NullPointerException ex){
				versionString="-1";
			}
			//end silliness
			String status =(String)list.get(3);
			if(StringUtils.equals(this.version,versionString) && !StringUtils.equals(id,exp.getId())){
				return XnatExperimentdata.getXnatExperimentdatasById(id,this.getUser(), false);
			}
		}
		
		return null;
		
	}
	
	
	
}