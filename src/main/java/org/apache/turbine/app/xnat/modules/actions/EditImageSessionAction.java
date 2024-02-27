//Copyright 2012 Radiologics, Inc
//Author: Tim Olsen <tim@radiologics.com>
package org.apache.turbine.app.xnat.modules.actions;

import org.apache.turbine.util.RunData;
import org.apache.velocity.context.Context;
import org.nrg.config.exceptions.ConfigServiceException;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.base.BaseElement;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.security.XDATUser;
import org.nrg.xdat.turbine.utils.TurbineUtils;
import org.nrg.xft.XFTItem;
import org.nrg.xft.event.EventUtils;
import org.nrg.xft.security.UserI;

import com.radiologics.utils.Esign;

/**
 * @author tim@radiologics.com
 *
 * New implementation of the Edit session action which handles E-signing.
 *
 */
public class EditImageSessionAction extends	org.nrg.xnat.turbine.modules.actions.EditImageSessionAction {

	@Override
	public void postProcessing(XFTItem item, RunData data, Context context)	throws Exception {
		super.postProcessing(item, data, context);
		
		if(data.getParameters().get("esign")!=null && Boolean.valueOf((String)data.getParameters().get("esign"))){
			String testament=null;
			try{
				testament=XDAT.getSiteConfigurationProperty("esig.testament");
			}catch (ConfigServiceException e) {
			}
			
			if(testament==null){
				testament="I ({firstname} {lastname}) certify that this data set is complete and accurate.";
			}
			
			UserI user=TurbineUtils.getUser(data);
			
			testament=testament.replace("{username}",user.getLogin()).replace("{firstname}",user.getFirstname()).replace("{lastname}",user.getLastname());
			
			(new Esign(TurbineUtils.getUser(data), (XnatExperimentdata)BaseElement.GetGeneratedItem(item), EventUtils.newEventInstance(EventUtils.CATEGORY.DATA, EventUtils.TYPE.PROCESS, "E-signed",testament, ""))).call();
		}
	}
	
}
