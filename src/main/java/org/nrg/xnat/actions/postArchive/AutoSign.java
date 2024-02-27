//Copyright 2012 Radiologics, Inc
//Author: Tim Olsen <tim@radiologics.com>
package org.nrg.xnat.actions.postArchive;

import java.util.Map;

import org.apache.log4j.Logger;
import org.nrg.config.exceptions.ConfigServiceException;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.om.XnatImagesessiondata;
import org.nrg.xft.event.EventUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.archive.PrearcSessionArchiver.PostArchiveAction;

import com.radiologics.utils.Esign;

/**
 * @author Tim Olsen
 *
 *	Auto sign operation is called whenever a session is archived.  Once the archiving has been completed, the session is e-signed if the 'esign' parameter is present.
 *  
 */
public class AutoSign implements PostArchiveAction {
	public static Logger logger = Logger.getLogger(AutoSign.class);
	
	@Override
	public Boolean execute(UserI user,XnatImagesessiondata src, Map<String,Object> params) {
		try {
			if(params.get("esign")!=null && Boolean.valueOf((String)params.get("esign"))){
				String testament=null;
				try{
					testament=XDAT.getSiteConfigurationProperty("esig.testament");
				}catch (ConfigServiceException e) {
				}
				
				if(testament==null){
					testament="I ({firstname} {lastname}) certify that this data set is complete and accurate.";
				}
				
				testament=testament.replace("{username}",user.getLogin()).replace("{firstname}",user.getFirstname()).replace("{lastname}",user.getLastname());
				
				(new Esign(user, src, EventUtils.newEventInstance(EventUtils.CATEGORY.DATA, EventUtils.TYPE.PROCESS, "E-signed",testament, ""))).call();
			}
			return true;
		} catch (Exception e) {
			logger.error("",e);
			return false;
		}
		
	}

}