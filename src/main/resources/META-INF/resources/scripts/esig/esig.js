//Copyright 2012 Radiologics, Inc

if(XNAT.app.locker==undefined){
	XNAT.app.locker=new Object();
}

if(XNAT.app.locker.locked==undefined){
	XNAT.app.locker.locked=false;
}

XNAT.app.esig= {
	msg:"esig_div",
	icon:"esig_a",
	
	showUnlock:function(){
		 XNAT.app.locker.locked=true;
		 YUIDOM.setStyle(YUIDOM.getElementsByClassName('lockable'), 'display', 'none');
	},
	
	showLock:function(){
		 YUIDOM.setStyle(YUIDOM.getElementsByClassName('lockable'), 'display', 'block');
	},
	   
	lock:function(){	
		var justification=new XNAT.app.esig.requestEsig("lock_","E-Sign",this._lock,this);
	},
   
   _lock:function(arg1,arg2,container){	   
		var event_reason=(container==undefined || container.dialog==undefined)?"":container.dialog.event_reason;
		var initCallback={
			success:function(obj1){
				closeModalPanel("lock_");
				XNAT.app.esig.showUnlock();
				XNAT.app.esig.status=true;
				window.location.reload();
			},
			failure:function(o){
	    		closeModalPanel("lock_");
				displayError("ERROR " + o.status+ ": Failed to sign item.");
			},
			scope:this
		}

		openModalPanel("lock_","E-Signing");
		if(XNAT.app.current_uri==undefined)alert("Missing URI definition");

		var params="";		
		params+="event_reason="+event_reason;
		params+="&event_type=WEB_FORM";
		params+="&event_action=E-signed";
		params+="&XNAT_CSRF="+csrfToken;
		params+="&EXPT_ID="+XNAT.app.esig.EXPT_ID;

		YAHOO.util.Connect.asyncRequest('POST',serverRoot+"/data/services/esig/sign?"+params,initCallback,null,this);
   },

   showEsigDialog:function(_yuioptions){
		this.yuioptions=_yuioptions;
		this.onResponse=new YAHOO.util.CustomEvent("response",this);

		if(this.yuioptions.width==undefined){
			this.yuioptions.width="400px";
		}	
		
		if(this.yuioptions.height==undefined){
			this.yuioptions.height="340px";
		}	
		
		if(this.yuioptions.close==undefined){
			this.yuioptions.close=true;
		}	
		this.yuioptions.underlay="shadow";
		this.yuioptions.modal=true;
		this.yuioptions.fixedcenter=true;
		this.yuioptions.visible=false;
		
		this.render=function(){
			this.panel=new YAHOO.widget.Dialog("justificationDialog",this.yuioptions);
			this.panel.setHeader(this.yuioptions.header);

			var bd = document.createElement("form");

			var table = document.createElement("table");
			var tb = document.createElement("tbody");
			table.appendChild(tb);
			bd.appendChild(table);
			
			//testament
			tr=document.createElement("tr");
			td1=document.createElement("th");
			td1.innerHTML=YUIDOM.get("esig_testament").innerHTML;
			td1.align="left";
			td1.colSpan="2";
			tr.appendChild(td1);
			tb.appendChild(tr);
			
			//password
			tr=document.createElement("tr");
			td1=document.createElement("th");
			td2=document.createElement("td");	
			td1.innerHTML="Password:";
			td1.align="left";
			var sel = document.createElement("input");
			sel.type="password";
			sel.id="esig_password";
			sel.name="esig_password";
			sel.onkeypress=function(e){
				return (e.keyCode != 13);
			}
			td2.appendChild(sel);
			tr.appendChild(td1);
			tr.appendChild(td2);
			tb.appendChild(tr);
			
			//password warning
			tr=document.createElement("tr");
			td1=document.createElement("th");
			td1.id="password_warning";
			td1.align="left";
			td1.colSpan="2";
			tr.appendChild(td1);
			tb.appendChild(tr);
			
			//justification
			tr=document.createElement("tr");
			td2=document.createElement("td");	
			var sel = document.createElement("input");
			sel.type="hidden";
			sel.value=YUIDOM.get("esig_testament").innerHTML;
			sel.id="event_reason";
			sel.name="event_reason";
			td2.appendChild(sel);
			tr.appendChild(td2);
			tb.appendChild(tr);
			
			//warning
			tr=document.createElement("tr");
			td1=document.createElement("td");
			td1.innerHTML="Note: Once signed, the data will be locked.  Should you need to modify the record after signing, you will need to follow the Repair Session link on the session report and supply an adequate justification for the modifications.";
			td1.align="left";
			td1.colSpan="2";
			tr.appendChild(td1);
			tb.appendChild(tr);

			this.panel.setBody(bd);

			this.panel.form=bd;

			this.panel.selector=this;
			var buttons=[{text:"Cancel",handler:{fn:function(){
						this.cancel();
					}}},{text:"Sign",handler:{fn:function(){
					this.selector.event_reason = this.form.event_reason.value;
					if(this.selector.event_reason==""){
						alert("Please enter a justification!");
						return;
					}
					if(this.form.esig_password.value==""){
						alert("Please enter a password!");
						return;
					}
					YUIDOM.get("password_warning").innerHTML="";
					
					var initCallback={
							success:function(o){
								this.cancel();
								this.selector.onResponse.fire();
							},
							failure:function(o){
								closeModalPanel("lock_");
								YUIDOM.get("password_warning").innerHTML="Invalid password";
								YUIDOM.get("password_warning").style.color="red";
							},
							scope:this
					}
					openModalPanel("lock_","E-Signing");
					
					var params="password=" + this.form.esig_password.value;
					params+="&username=" + YUIDOM.get("esig_username").innerHTML;
					
					YAHOO.util.Connect.asyncRequest('PUT',serverRoot+"/data/services/auth",initCallback,params,this);
				}},isDefault:true}];
			this.panel.cfg.queueProperty("buttons",buttons);


			this.panel.render("page_body");
			this.panel.show();
		}
	},
	requestEsig:function(_id,_header,_function,scope){
		this.id=_id;
		this.onCompletion=new YAHOO.util.CustomEvent("complete",this);
		
		this.options=new Object();
		this.options.header=_header;
		
		this.dialog=new XNAT.app.esig.showEsigDialog(this.options);
		this.dialog.id=_id;
		this.dialog.header=_header;
		this.dialog.onResponse.subscribe(_function,this,scope);

		this.dialog.render();
	},
	   
	unlock:function(){	
		var justification=new XNAT.app.esig.requestNewVersion("lock_","Create New Version",this._unlock,this);
	},
   
   _unlock:function(arg1,arg2,container){	   
		var event_reason=(container==undefined || container.dialog==undefined)?"":container.dialog.event_reason;
		var initCallback={
			success:function(obj1){
				closeModalPanel("lock_");
				XNAT.app.esig.showLock();
				XNAT.app.esig.status=true;
				window.location.reload();
			},
			failure:function(o){
	    		closeModalPanel("lock_");
				displayError("ERROR " + o.status+ ": Failed to create new version.");
			},
			scope:this
		}

		openModalPanel("lock_","Creating New Version");
		if(XNAT.app.current_uri==undefined)alert("Missing URI definition");

		var params="";		
		params+="event_reason="+event_reason;
		params+="&event_type=WEB_FORM";
		params+="&event_action=Created New Version";
		params+="&XNAT_CSRF="+csrfToken;
		params+="&EXPT_ID="+XNAT.app.esig.EXPT_ID;

		YAHOO.util.Connect.asyncRequest('POST',serverRoot+"/data/services/esig/version?"+params,initCallback,null,this);
   },

   showNewVersionDialog:function(_yuioptions){
		this.yuioptions=_yuioptions;
		this.onResponse=new YAHOO.util.CustomEvent("response",this);

		if(this.yuioptions.width==undefined){
			this.yuioptions.width="400px";
		}	
		
		if(this.yuioptions.height==undefined){
			this.yuioptions.height="220px";
		}	
		
		if(this.yuioptions.close==undefined){
			this.yuioptions.close=true;
		}	
		this.yuioptions.underlay="shadow";
		this.yuioptions.modal=true;
		this.yuioptions.fixedcenter=true;
		this.yuioptions.visible=false;
		
		this.render=function(){
			this.panel=new YAHOO.widget.Dialog("justificationDialog",this.yuioptions);
			this.panel.setHeader(this.yuioptions.header);

			var bd = document.createElement("form");

			var table = document.createElement("table");
			var tb = document.createElement("tbody");
			table.appendChild(tb);
			bd.appendChild(table);

			//warning
			tr=document.createElement("tr");
			td1=document.createElement("td");
			td1.style.color="red";
			td1.innerHTML="The correctness of this data has been attested to.  It can no longer be modified or deleted.  Are you sure you want to create a new version of this data.";
			td1.align="left";
			td1.colSpan="2";
			tr.appendChild(td1);
			tb.appendChild(tr);
			
			//justification
			tr=document.createElement("tr");
			td1=document.createElement("th");
			td2=document.createElement("td");	
			td1.innerHTML="Justification:";
			td1.align="left";
			var sel = document.createElement("textarea");
			sel.cols="24";
			sel.rows="4";
			sel.id="event_reason";
			sel.name="event_reason";
			td2.appendChild(sel);
			tr.appendChild(td1);
			tr.appendChild(td2);
			tb.appendChild(tr);

			this.panel.setBody(bd);

			this.panel.form=bd;

			this.panel.selector=this;
			var buttons=[{text:"Confirm",handler:{fn:function(){
					this.selector.event_reason = this.form.event_reason.value;
					if(this.selector.event_reason==""){
						alert("Please enter a justification!");
						return;
					}

					this.cancel();
					this.selector.onResponse.fire();
				}},isDefault:true},
				{text:"Cancel",handler:{fn:function(){
					this.cancel();
				}}}];
			this.panel.cfg.queueProperty("buttons",buttons);


			this.panel.render("page_body");
			this.panel.show();
		}
	},
	requestNewVersion:function(_id,_header,_function,scope){
		this.id=_id;
		this.onCompletion=new YAHOO.util.CustomEvent("complete",this);
		
		this.options=new Object();
		this.options.header=_header;
		
		this.dialog=new XNAT.app.esig.showNewVersionDialog(this.options);
		this.dialog.id=_id;
		this.dialog.header=_header;
		this.dialog.onResponse.subscribe(_function,this,scope);

		this.dialog.render();
	}
}