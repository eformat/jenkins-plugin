package com.openshift.jenkins.plugins.pipeline;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftPluginDescriptorValidation;
import hudson.Launcher;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.model.TaskListener;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

//import com.openshift.internal.restclient.http.HttpClientException;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.capability.CapabilityVisitor;
import com.openshift.restclient.capability.resources.IBuildCancelable;
import com.openshift.restclient.model.IBuild;

import javax.servlet.ServletException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class OpenShiftBuildCanceller extends OpenShiftBasePostAction {
	
	protected final static String DISPLAY_NAME = "Cancel OpenShift Builds";
	
	public String getDisplayName() {
		return DISPLAY_NAME;
	}
	
    protected final String bldCfg;    
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftBuildCanceller(String apiURL, String namespace, String authToken, String verbose, String bldCfg) {
    	super(apiURL, namespace, authToken, verbose);
        this.bldCfg = bldCfg;
    }

    // generically speaking, Jenkins will always pass in non-null field values.  However, as we have periodically
    // added new fields, jobs created with earlier versions of the plugin get null for the new fields.  Hence, 
    // we have introduced the generic convention (even for fields that existed in the initial incarnations of the plugin)
    // of insuring nulls are not returned for field getters

    public String getBldCfg() {
    	return bldCfg;
    }
    
    public String getBldCfg(Map<String,String> overrides) {
		return getOverride(getBldCfg(), overrides);
    }
	
	public boolean coreLogic(Launcher launcher, TaskListener listener, Map<String,String> overrides) {
		boolean chatty = Boolean.parseBoolean(verbose);
				
    	listener.getLogger().println(String.format(MessageConstants.START_BUILD_RELATED_PLUGINS, DISPLAY_NAME, getBldCfg(overrides), getNamespace(overrides)));
		
    	// get oc client 
    	IClient client = getClient(listener, DISPLAY_NAME, overrides);
    	
    	if (client != null) {
/*			try {
*/				List<IBuild> list = client.list(ResourceKind.BUILD, getNamespace(overrides));
				int count = 0;
				for (IBuild bld : list) {
					String phaseStr = bld.getStatus();
					
					// if build active, let's cancel it
					String buildName = bld.getName();
					if (buildName.startsWith(getBldCfg(overrides)) && !isBuildFinished(phaseStr)) {
						if (chatty)
							listener.getLogger().println("\nOpenShiftBuildCanceller found active build " + buildName);
						
						// re-get bld (etcd employs optimistic update)
						bld = client.get(ResourceKind.BUILD, buildName, getNamespace(overrides));
						
						// call cancel api
	    				bld.accept(new CapabilityVisitor<IBuildCancelable, IBuild>() {
		    				public IBuild visit(IBuildCancelable cancelable) {
		    					return cancelable.cancel();
		    				}
		    			}, null);
	    										
	    				listener.getLogger().println(String.format(MessageConstants.CANCELLED_BUILD, buildName));
	    				count++;
					
					}
				}

		    	listener.getLogger().println(String.format(MessageConstants.EXIT_BUILD_CANCEL, DISPLAY_NAME, count));
				
				return true;
/*			} catch (HttpClientException e1) {
				e1.printStackTrace(listener.getLogger());
				return false;
			}
*/    	} else {
    		return false;
    	}
	}

	// Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

	/**
     * Descriptor for {@link OpenShiftBuildCanceller}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> implements IOpenShiftPluginDescriptorValidation {
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */

        /**
         * In order to load the persisted global configuration, you have to 
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }


        public FormValidation doCheckBldCfg(@QueryParameter String value)
                throws IOException, ServletException {
        	return ParamVerify.doCheckBldCfg(value);
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return DISPLAY_NAME;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // pull info from formData, set appropriate instance field (which should have a getter), and call save().
            save();
            return super.configure(req,formData);
        }

    }

}

