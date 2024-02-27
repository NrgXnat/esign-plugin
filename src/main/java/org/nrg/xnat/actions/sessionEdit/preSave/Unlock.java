//Copyright 2012 Radiologics, Inc
//Author: Tim Olsen <tim@radiologics.com>
package org.nrg.xnat.actions.sessionEdit.preSave;

import java.util.Map;

import org.apache.commons.collections.Predicate;
import org.nrg.xdat.om.XnatSubjectassessordata;
import org.nrg.xft.XFTItem;
import org.nrg.xft.db.ViewManager;
import org.nrg.xft.event.persist.PersistentWorkflowI;
import org.nrg.xft.exception.InvalidPermissionException;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.turbine.modules.actions.ModifySubjectAssessorData.PreSaveAction;

/**
 * @author tim@radiologics.com
 *
 * Presave implementation used in ModifySubjAssessor to unlock/version data before modifying it.
 *
 */
public class Unlock implements PreSaveAction{

	@Override
	public void execute(UserI user, XnatSubjectassessordata src, Map<String, String> params, PersistentWorkflowI wrk) throws Exception {

		XFTItem existing=src.getItem().getCurrentDBVersion();
		if(existing==null){
			return;
		}else if(existing.checkStatus(new Predicate(){
			public boolean evaluate(Object arg0) {
				if(arg0 !=null && arg0 instanceof String){
					return (ViewManager.ACTIVE.equals(arg0) || ViewManager.QUARANTINE.equals(arg0));
				}
				return false;
			}},false)){
			//no changes required
		}else if(existing.checkStatus(new Predicate(){
			public boolean evaluate(Object arg0) {
				if(arg0 !=null && arg0 instanceof String){
					return (ViewManager.LOCKED.equals(arg0));
				}
				return false;
			}},false)){
			throw new InvalidPermissionException("This session is locked and cannot be modified.");
		}else{
			throw new InvalidPermissionException("This session is locked and cannot be modified.");
		}
	}
}
