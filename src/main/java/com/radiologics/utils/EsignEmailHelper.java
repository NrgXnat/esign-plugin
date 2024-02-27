package com.radiologics.utils;

import org.apache.log4j.Logger;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.preferences.SiteConfigPreferences;
import org.nrg.xdat.turbine.utils.TurbineUtils;
import org.nrg.xft.security.UserI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EsignEmailHelper {

    public static final String DEFAULT_LOCKED_SUBJECT = "SITE_ID: PROJECT_LABEL Image Data QC passed for Session EXP_LABEL";
    public static final String DEFAULT_LOCKED_EMAIL = "<p>Hello All, </p>Thank you for your submission of subject EXP_LINK." +
            " We have completed the Quality Control and all data is acceptable. " +
            " Please do NOT respond directly to this email. Should you have any questions" +
            " or concerns, contact USER_EMAIL. Thank you.";

    public static final String DEFAULT_UNLOCKED_SUBJECT = "SITE_ID: PROJECT_LABEL EXP_LABEL Unlocked";
    public static final String DEFAULT_UNLOCKED_EMAIL = "<p>EXP_LINK has been unlocked.</p> Thank you.";

    public static final String DEFAULT_COMPLETE_REUPLOAD_SUBJECT = "SITE_ID: PROJECT_LABEL EXP_LABEL marked for a complete reupload";
    public static final String DEFAULT_COMPLETE_REUPLOAD_EMAIL = "<p>EXP_LINK has been marked for complete reupload. All files removed. </p> Thank you.";

    public static final String DEFAULT_PARTIAL_REUPLOAD_SUBJECT = "SITE_ID: PROJECT_LABEL EXP_LABEL marked for a partial reupload";
    public static final String DEFAULT_PARTIAL_REUPLOAD_EMAIL = "<p>EXP_LINK has been marked for partial reupload. Nothing removed. </p> Thank you.";

    public static final String DEFAULT_SCANS_ONLY_REUPLOAD_SUBJECT = "SITE_ID: PROJECT_LABEL EXP_LABEL marked for scans only reupload";
    public static final String DEFAULT_SCANS_ONLY_REUPLOAD_EMAIL = "<p>EXP_LINK has been marked for scans only reupload. Scans removed. </p> Thank you.";

    public static final String DEFAULT_COMPLETE_RENAME_REUPLOAD_SUBJECT = "SITE_ID: PROJECT_LABEL EXP_LABEL marked for a complete reupload and removed from project";
    public static final String DEFAULT_COMPLETE_RENAME_REUPLOAD_EMAIL = "<p>EXP_LINK has been marked for a complete reupload. Experiment has been removed from the project. </p> Thank you.";


    public static Logger logger = Logger.getLogger(Esign.class);

    public static void sendEmail(XnatExperimentdata exp, UserI user, String subjectTemplate, String bodyTemplate) throws Exception{
        sendEmail(exp, user, subjectTemplate, bodyTemplate, null);
    }

    public static void sendEmail(XnatExperimentdata exp, UserI user, String subjectTemplate, String bodyTemplate, List<String> emails) throws Exception{
        try {
            final List<String> recipients = emails == null ? getEmailsFromProject(exp.getProjectData().getId()) : emails;
            if(recipients == null || recipients.isEmpty()){
                logger.debug("Recipient list is empty. Not sending esign action email.");
                return;
            }

            final SiteConfigPreferences siteConfigPreferences = XDAT.getContextService().getBeanSafely(SiteConfigPreferences.class);
            final String adminEmail = XDAT.getSiteConfigPreferences().getAdminEmail();
            final String expLink = "<a href=\"" + TurbineUtils.GetFullServerPath() + "/app/action/DisplayItemAction/search_element/" +
                    exp.getXSIType()+"/search_field/" + exp.getXSIType() + ".ID/search_value/" +
                    exp.getId()+"/popup/false/project/" + exp.getProject() + "\">" + exp.getLabel() + "</a>";

            final Map<String, String> properties = new HashMap<>();
            properties.put("EXP_LINK", expLink);
            properties.put("SITE_ID", siteConfigPreferences.getSiteId());
            properties.put("PROJECT_LABEL", exp.getProjectData().getId());
            properties.put("USER_EMAIL", user.getEmail());
            properties.put("EXP_LABEL", exp.getLabel());

            final String body    = "<html><body>" + buildStringFromTemplate(bodyTemplate, properties) + "</body></html>";
            final String subject = buildStringFromTemplate(subjectTemplate, properties);

            logger.debug(body);
            XDAT.getMailService().sendHtmlMessage(adminEmail, recipients.toArray(new String[recipients.size()]), subject, body);
        } catch (Exception e) {
            logger.error("Failed to send email.", e);
        }
    }

    public static final String buildStringFromTemplate(String template, Map<String,String>properties){
        for(Map.Entry<String, String> property : properties.entrySet()){
            template = template.replaceAll(property.getKey(),property.getValue());
        }
        return template;
    }

    public static List<String> getEmailsFromProject(String projectId) {
        String QUERY_PROJECT_EMAILS = "SELECT distinct email FROM xdat_userGroup g RIGHT JOIN xdat_user_Groupid map ON g.id=map.groupid RIGHT JOIN xdat_user u ON map.groups_groupid_xdat_user_xdat_user_id=u.xdat_user_id WHERE tag='" + projectId + "' and enabled = 1 ORDER BY email DESC;";
        return XDAT.getJdbcTemplate().queryForList(QUERY_PROJECT_EMAILS,String.class);
    }
}
