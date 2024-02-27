//Copyright 2012 Radiologics, Inc
//Author: Tim Olsen <tim@radiologics.com>
package com.radiologics.utils;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Callable;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.nrg.xdat.base.BaseElement;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.model.XnatExperimentdataI;
import org.nrg.xdat.model.XnatExperimentdataShareI;
import org.nrg.xdat.model.XnatImageassessordataI;
import org.nrg.xdat.model.XnatImagescandataI;
import org.nrg.xdat.model.XnatReconstructedimagedataI;
import org.nrg.xdat.om.XnatAbstractresource;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatImagesessiondata;
import org.nrg.xdat.om.XnatResource;
import org.nrg.xdat.om.XnatResourceseries;
import org.nrg.xdat.om.base.BaseXnatExperimentdata.UnknownPrimaryProjectException;
import org.nrg.xft.XFTItem;
import org.nrg.xft.event.EventDetails;
import org.nrg.xft.exception.ElementNotFoundException;
import org.nrg.xft.exception.FieldNotFoundException;
import org.nrg.xft.schema.Wrappers.XMLWrapper.SAXReader;
import org.nrg.xft.security.UserI;
import org.nrg.xft.utils.SaveItemHelper;
import org.nrg.xnat.exceptions.InvalidArchiveStructure;
import org.nrg.xnat.restlet.representations.ItemXMLRepresentation;
import org.restlet.data.MediaType;
import org.restlet.resource.Representation;

/**
 * Copy implementation for creating a full/deep copy of any XnatExperimentdata extension.
 * @author tim@radiologics.com
 * @author rpfujiw@gmail.com
 *
 */
public class SessionCopyImpl implements Callable<XnatExperimentdata>{
    private static final String TIMESTAMP = "yyyyMMdd_HHmmss";

	private static final String PATTERN = "_v[0-9]{1,8}_\\d+$";

	public static Logger logger = Logger.getLogger(SessionCopyImpl.class);

    private static final long serialVersionUID = 8092462697472805786L;
    final XnatExperimentdata exp;
    final UserI user;
    String dateAppend = null;
    String newId = null;
    EventDetails e;
    
    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(PATTERN); 
    /**
     * @param expt experiment to be copied
     * @param user for operation
     * @param e event details for the transaction
     */
    public SessionCopyImpl(XnatExperimentdata expt, UserI user, EventDetails e) {
        exp=expt;
        this.user=user;
        this.e=e;
    }
   
    /**
     * @param newEXPT experiment for correction
     * @param dateAppend date to be appended
     * @return corrected id
     */
    private String correctIDandLabel(XnatExperimentdataI newEXPT, String dateAppend){
    	
	    String id = newEXPT.getId();
        String newid=id+ "_" + dateAppend;
        //correct ID
        newEXPT.setId(newid);
        String label = newEXPT.getLabel();
        
        //correct label
        newEXPT.setLabel(label+ "_" + dateAppend);
       
        //correct shared projects
        for(XnatExperimentdataShareI share: newEXPT.getSharing_share()){
            if(share.getLabel()!=null){
            	String label2 = share.getLabel();
                share.setLabel(label2+ "_" + dateAppend);
            }
        }
        return newid;
    }
   
    /**
     * @param resource resouce to be modified
     * @param filepath old file path
     * @param newFilepath new file path
     * @throws UnknownPrimaryProjectException unknown project
     * @throws InvalidArchiveStructure invalid archive structure
     * @throws ElementNotFoundException element not found
     * @throws FieldNotFoundException field not found
     */
    private void modifyResource(XnatAbstractresourceI resource, String filepath, String newFilepath) throws UnknownPrimaryProjectException, InvalidArchiveStructure, ElementNotFoundException, FieldNotFoundException{
        String oldID = (String) exp.getProperty("label"); 

    	if(resource instanceof XnatResource){
            String path=((XnatResource)resource).getUri();
            String newURI = path.replace(exp.getArchiveRootPath()+"arc001/"+oldID, newFilepath);
            ((XnatResource) resource).setUri(newURI);
        }else if(resource instanceof XnatResourceseries){
            String path=((XnatResourceseries)resource).getPath();
            String newURI = path.replace(exp.getArchiveRootPath()+"arc001/"+oldID, newFilepath);
            ((XnatResourceseries) resource).setPath(newURI);
        }
    }

    @Override
    public XnatExperimentdata call() throws Exception {
            //copy directory
    		
    		//build expected session directory path
            String filepath = exp.getArchiveRootPath() +"arc001/" + exp.getArchiveDirectoryName();
            
            //build timestamped version of path
            final DateFormat dateFormat = new SimpleDateFormat(TIMESTAMP);
            final Date date = new Date();
            dateAppend = dateFormat.format(date);
            final String newFilepath = filepath + "_" + dateAppend;
            new File(newFilepath).mkdirs();
            File source = new File(filepath);
            File dest = new File(newFilepath);
            
            //copy the actual files
            try {
            	if(source.exists()){
            		FileUtils.copyDirectory(source, dest);
            	}
            } catch (IOException e) {
                logger.error("",e);
                throw e;
                //don't continue if the file copy failed
            }
           
            //copy xml to create new experiment (without hidden primary keys
            Representation representation = new ItemXMLRepresentation(exp.getItem(), MediaType.TEXT_XML, false,false);
            SAXReader reader = new SAXReader(user);
            XFTItem item = reader.parse(representation.getStream());
            XnatExperimentdata newEXPT=(XnatExperimentdata)BaseElement.GetGeneratedItem(item);
            
            newId = correctIDandLabel(newEXPT, dateAppend);
            
            if (!newId.equals(exp.getId())) {
                for(final XnatAbstractresourceI res: newEXPT.getResources_resource()){
                    modifyResource((XnatAbstractresource)res,filepath,newFilepath);
                }

                if(newEXPT instanceof XnatImagesessiondata){
                    for(final XnatImagescandataI scan: ((XnatImagesessiondata)newEXPT).getScans_scan()){
                        scan.setImageSessionId(newId);

                        for(final XnatAbstractresourceI res: scan.getFile()){
                            modifyResource((XnatAbstractresource)res,filepath,newFilepath);
                        }
                    }

                    for(final XnatReconstructedimagedataI recon: ((XnatImagesessiondata)newEXPT).getReconstructions_reconstructedimage()){
                        recon.setId(recon.getId()+"_" + dateAppend);

                        recon.setImageSessionId(newId);

                        for(final XnatAbstractresourceI res: recon.getIn_file()){
                            modifyResource((XnatAbstractresource)res,filepath,newFilepath);
                        }

                        for(final XnatAbstractresourceI res: recon.getOut_file()){
                            modifyResource((XnatAbstractresource)res,filepath,newFilepath);
                        }
                    }

                    for(final XnatImageassessordataI assess: ((XnatImagesessiondata)newEXPT).getAssessors_assessor()){
                        assess.setImagesessionId("NULL");

                        correctIDandLabel(assess, "_"+dateAppend);

                        for(final XnatAbstractresourceI res: assess.getResources_resource()){
                            modifyResource((XnatAbstractresource)res,filepath,newFilepath);
                        }

                        for(final XnatAbstractresourceI res: assess.getIn_file()){
                            modifyResource((XnatAbstractresource)res,filepath,newFilepath);
                        }

                        for(final XnatAbstractresourceI res: assess.getOut_file()){
                            modifyResource((XnatAbstractresource)res,filepath,newFilepath);
                        }
                    }
                }
            }
        
        try {
        	newEXPT.setOriginal(exp.getId());
            if(newEXPT.getVersion()==null){
                newEXPT.setVersion(new Integer(1));
            }
        	
        	SaveItemHelper.authorizedSave(newEXPT.getItem(), user, false, false, e);
        
        } catch (Exception e) {
			logger.error("",e);
			throw e;
		}
        return newEXPT;
    }

}
