XNAT.app.corrections= {
	msg:"esig_div",
	icon:"esig_a",
	
	reupload:function(status){
		var redirect = serverRoot + (this.reupload_redirect ? this.reupload_redirect : "/app/template/XDATScreen_add_experiment.vm" +
		                                                                               "/project/"       + this.project +
		                                                                               "/subject_id/"    + this.subject_id +
                                                                                       "/subject_label/" + this.subject_label +
                                                                                       "/visit_id/"      + this.visit_id );
		var opts={height:"250px",
				message:"Continuing with this process will remove the existing files and metadata for this session.  Please provide a justification for this action.",
				note:"Warning: The next step can take several minutes to complete."
		};

		if(status == 'locked' || status == 'obsolete'){
			XNAT.app.requestJustification("reupload","Session Reupload",function(arg1,arg2,container){
				var event_reason=(container==undefined || container.dialog==undefined)?"":container.dialog.event_reason;
				this.initCallback={
					success:function(obj1){
			    		window.location = redirect;
					},
					failure:function(o){
			    		closeModalPanel("file");
						displayError("ERROR " + o.status+ ": Failed to reset session.");
					},
		            cache:false, // Turn off caching for IE
					scope:this
				}
				
				openModalPanel("file","Deleting existing resources");
				var params="";		
				params+="event_reason="+event_reason;
				params+="&event_type=WEB_FORM";
				params+="&event_action=Prepared session for reupload";
				params+="&EXPT_ID="+XNAT.app.esig.EXPT_ID;
				
				YAHOO.util.Connect.asyncRequest('POST',serverRoot+'/REST/services/esig/reset?XNAT_CSRF=' + csrfToken + '&'+params,this.initCallback,null,this);
			},this,opts);
		}else{
    		window.location = redirect;
		}
	},
	
	modify:function(status){
		var opts={height:"250px",message:"Warning: Continuing with this process will unlock this session for modification.  Please provide a justification for this action."};
		
		
		if(status == 'locked' || status == 'obsolete'){
			XNAT.app.requestJustification("reupload","Session Modification",function(arg1,arg2,container){
				var event_reason=(container==undefined || container.dialog==undefined)?"":container.dialog.event_reason;
				this.initCallback={
					success:function(obj1){
			    		window.location=serverRoot+"/app/action/XDATActionRouter/xdataction/edit/search_element/"+this.data_type+"/search_field/"+this.data_type+".ID/search_value/"+this.expt_id+"/popup/false/project/"+this.project;
					},
					failure:function(o){
			    		closeModalPanel("file");
						displayError("ERROR " + o.status+ ": Failed to reset session.");
					},
		            cache:false, // Turn off caching for IE
					scope:this
				}
				
				openModalPanel("file","Unlocking session for modification");
				var params="";		
				params+="event_reason="+event_reason;
				params+="&event_type=WEB_FORM";
				params+="&event_action=Unlocked session for modification";
				params+="&EXPT_ID="+XNAT.app.esig.EXPT_ID;
				
				YAHOO.util.Connect.asyncRequest('POST',serverRoot+'/REST/services/esig/version?XNAT_CSRF=' + csrfToken + '&'+params,this.initCallback,null,this);
			},this,opts);
		}else{
    		window.location=serverRoot+"/app/action/XDATActionRouter/xdataction/edit/search_element/"+this.data_type+"/search_field/"+this.data_type+".ID/search_value/"+this.expt_id+"/popup/false/project/"+this.project;

		}
	},
	
	obsolete:function(){
		var opts={height:"250px",message:"Warning: Continuing with this process will remove data from this project.  Please provide a justification for this action."};
		XNAT.app.requestJustification("reupload","Data Deletion",function(arg1,arg2,container){
			var event_reason=(container==undefined || container.dialog==undefined)?"":container.dialog.event_reason;
			this.initCallback={
				success:function(obj1){
		    		window.location=serverRoot+"/data/projects/"+this.project+"?format=html";
				},
				failure:function(o){
		    		closeModalPanel("file");
					displayError("ERROR " + o.status+ ": Failed to reset session.");
				},
	            cache:false, // Turn off caching for IE
				scope:this
			}
			
			openModalPanel("file","Deleting session");
			var params="";		
			params+="event_reason="+event_reason;
			params+="&event_type=WEB_FORM";
			params+="&event_action=Deleted";
			params+="&EXPT_ID="+XNAT.app.esig.EXPT_ID;
			
			YAHOO.util.Connect.asyncRequest('POST',serverRoot+'/REST/services/esig/obsolete?XNAT_CSRF=' + csrfToken + '&'+params,this.initCallback,null,this);
		},this,opts);
	},
	preparereupload:function(){
		var opts={
				height:"250px",
				width:"405px",
				note:"Warning: The next step can take several minutes to complete.",
				checkbox:"Remove: <input type='radio' name='reupload_choice' id='reupload_partial' value='false' checked>Nothing <input type='radio' id='reupload_scans' name='reupload_choice' value='false'> Scans Only <input type='radio' name='reupload_choice' id='reupload_delete_files' value='true'>Everything <input type='radio' name='reupload_choice' id='reupload_delete_all' value='true'>From Project",
				message:"In preparation for reupload, this session will be moved to quarantine and additional files can be uploaded. Choose from the options below. Please provide a justification for this action."
					};
		XNAT.app.requestJustification("reupload","Prepare Reupload",function(arg1,arg2,container){
			var event_reason=(container==undefined || container.dialog==undefined)?"":container.dialog.event_reason;
			this.initCallback={
				success:function(obj1){
					var deleteAll = document.getElementById("reupload_delete_all");
					if( deleteAll && deleteAll.checked){
						window.location=serverRoot+"/data/projects/"+this.project+"?format=html";
					}else{
						window.location=serverRoot+"/data/experiments/"+XNAT.app.esig.EXPT_ID+"?format=html";
					}
				},
				failure:function(o){
		    		closeModalPanel("file");
					displayError("ERROR " + o.status+ ": Failed to reset session.");
				},
	            cache:false, // Turn off caching for IE
				scope:this
			}
			
			var params="";		
			params+="event_reason="+event_reason;
			params+="&event_type=WEB_FORM";
			params+="&EXPT_ID="+XNAT.app.esig.EXPT_ID;
			var that=this;
			if(document.getElementById("reupload_delete_files").checked){
				params+="&event_action=CompleteReupload";
				xModalConfirm({
					  message: "Confirm",
				      content: "Warning: Continuing with this process will move the session to quarantine and remove ALL existing scans and metadata. Please confirm before continuing?",
				      okAction: function(){
							openModalPanel("file","Moving session to quarantine");
							YAHOO.util.Connect.asyncRequest('POST',serverRoot+'/REST/services/esig/complete?XNAT_CSRF=' + csrfToken + '&'+params,that.initCallback,null,that);
				      },
				      cancelAction: function(){
				    		window.location=serverRoot+"/data/experiments/"+XNAT.app.esig.EXPT_ID+"?format=html";
				      }
				    });
			}else if(document.getElementById("reupload_partial").checked){
				params+="&event_action=PartialReupload";
				xModalConfirm({
					  message: "Confirm",
				      content: "Warning: Continuing with this process will move the session to quarantine and allow additional uploads. Please confirm before continuing?",
				      okAction: function(){
							openModalPanel("file","Moving session to quarantine");
							YAHOO.util.Connect.asyncRequest('POST',serverRoot+'/REST/services/esig/partial?XNAT_CSRF=' + csrfToken + '&'+params,that.initCallback,null,that);
				      },
				      cancelAction: function(){
				    		window.location=serverRoot+"/data/experiments/"+XNAT.app.esig.EXPT_ID+"?format=html";

				      }
				    });
			}else if(document.getElementById("reupload_scans").checked){
				params+="&event_action=ScanReupload";
				xModalConfirm({
					  message: "Confirm",
				      content: "Warning: Continuing with this process will move the session to quarantine and remove existing scans. Please confirm before continuing?",
				      okAction: function(){
							openModalPanel("file","Moving session to quarantine");
							YAHOO.util.Connect.asyncRequest('POST',serverRoot+'/REST/services/esig/reuploadscans?XNAT_CSRF=' + csrfToken + '&'+params,that.initCallback,null,that);
				      },
				      cancelAction: function(){
				    		window.location=serverRoot+"/data/experiments/"+XNAT.app.esig.EXPT_ID+"?format=html";

				      }
				    });
			}else if(document.getElementById("reupload_delete_all").checked){
				params+="&event_action=Delete";
				xModalConfirm({
					  message: "Confirm",
				      content: "Warning: Continuing with this process will remove this session from this project. Please confirm before continuing?",
				      okAction: function(){
							openModalPanel("file","Deleting session");
							YAHOO.util.Connect.asyncRequest('POST',serverRoot+'/REST/services/esig/completeandrename?XNAT_CSRF=' + csrfToken + '&'+params,that.initCallback,null,that);
				      },
				      cancelAction: function(){
				    		window.location=serverRoot+"/data/experiments/"+XNAT.app.esig.EXPT_ID+"?format=html";

				      }
				    });
			}else{
	    		window.location=serverRoot+"/data/experiments/"+XNAT.app.esig.EXPT_ID+"?format=html";
			}
		},this,opts);
	},
	rollback:function(status,existingversion,version){
		var opts={height:"250px",message:"Warning: Continuing with this process will rollback the session to a previous version.  Please provide a justification for this action."};
		
		
		if(status != 'locked'){
			XNAT.app.requestJustification("rollback","Rollback. Create version "+(existingversion+1)+" from "+version,function(arg1,arg2,container){
				var event_reason=(container==undefined || container.dialog==undefined)?"":container.dialog.event_reason;
				this.initCallback={
					success:function(obj1){
						window.location=serverRoot+"/data/experiments/"+XNAT.app.esig.EXPT_ID+"?format=html";					},
					failure:function(o){
			    		closeModalPanel("file");
						displayError("ERROR " + o.status+ ": Failed to rollback session.");
					},
		            cache:false, // Turn off caching for IE
					scope:this
				}
				
				openModalPanel("file","Rollback. Create version "+(existingversion+1));
				var params="";		
				params+="event_reason="+event_reason;
				params+="&event_type=WEB_FORM";
				params+="&event_action=Rollback. Create version "+(existingversion+1)+" from "+version;
				params+="&EXPT_ID="+XNAT.app.esig.EXPT_ID;
				params+="&version="+version;
				params+="&override=true";
				
				
				YAHOO.util.Connect.asyncRequest('POST',serverRoot+'/REST/services/esig/rollback?XNAT_CSRF=' + csrfToken + '&'+params,this.initCallback,null,this);
			},this,opts);
		}else{
			window.location=serverRoot+"/data/experiments/"+XNAT.app.esig.EXPT_ID+"?format=html";
		}
	},
	active:function(status){
		var opts={height:"250px",message:"Warning: Continuing with this process will remove the session from quarantine.  Please provide a justification for this action."};
		
		
		if(status != 'locked' ){
			XNAT.app.requestJustification("active","Remove the session from quarantine. ",function(arg1,arg2,container){
				var event_reason=(container==undefined || container.dialog==undefined)?"":container.dialog.event_reason;
				this.initCallback={
					success:function(obj1){
						window.location=serverRoot+"/data/experiments/"+XNAT.app.esig.EXPT_ID+"?format=html";					},
					failure:function(o){
			    		closeModalPanel("file");
						displayError("ERROR " + o.status+ ": Failed to remove the session from quarantine.");
					},
		            cache:false, // Turn off caching for IE
					scope:this
				}
				
				openModalPanel("file","Remove the session from quarantine");
				var params="";		
				params+="event_reason="+event_reason;
				params+="&event_type=WEB_FORM";
				params+="&event_action=Remove the session from quarantine";
				params+="&EXPT_ID="+XNAT.app.esig.EXPT_ID;
				params+="&override=true";
				
				
				YAHOO.util.Connect.asyncRequest('POST',serverRoot+'/REST/services/esig/active?XNAT_CSRF=' + csrfToken + '&'+params,this.initCallback,null,this);
			},this,opts);
		}else{
			window.location=serverRoot+"/data/experiments/"+XNAT.app.esig.EXPT_ID+"?format=html";
		}
	}
}