package com.oneidentity.safeguard.safeguardjava;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oneidentity.safeguard.safeguardjava.data.A2ARegistration;
import com.oneidentity.safeguard.safeguardjava.data.A2ARetrievableAccount;
import com.oneidentity.safeguard.safeguardjava.data.A2ARetrievableAccountInternal;
import com.oneidentity.safeguard.safeguardjava.data.BrokeredAccessRequest;
import com.oneidentity.safeguard.safeguardjava.data.CertificateContext;
import com.oneidentity.safeguard.safeguardjava.event.ISafeguardEventListener;
import com.oneidentity.safeguard.safeguardjava.event.SafeguardEventListener;
import com.oneidentity.safeguard.safeguardjava.exceptions.ArgumentException;
import com.oneidentity.safeguard.safeguardjava.exceptions.ObjectDisposedException;
import com.oneidentity.safeguard.safeguardjava.exceptions.SafeguardForJavaException;
import com.oneidentity.safeguard.safeguardjava.restclient.RestClient;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.oneidentity.safeguard.safeguardjava.event.ISafeguardEventHandler;
import com.oneidentity.safeguard.safeguardjava.event.PersistentSafeguardA2AEventListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.client.methods.CloseableHttpResponse;

public class SafeguardA2AContext implements ISafeguardA2AContext {

    private boolean disposed;

    private final String networkAddress;
    private final boolean ignoreSsl;
    private final int apiVersion;
    private final CertificateContext clientCertificate;

    private final RestClient a2AClient;
    private final RestClient coreClient;

    
    public SafeguardA2AContext(String networkAddress, String certificateAlias, String certificatePath,
            char[] certificatePassword, int apiVersion, boolean ignoreSsl) {
        this.networkAddress = networkAddress;
        
        String safeguardA2AUrl = String.format("https://%s/service/a2a/v%d", this.networkAddress, apiVersion);
        this.a2AClient = new RestClient(safeguardA2AUrl, ignoreSsl);
        String safeguardCoreUrl = String.format("https://%s/service/core/v%d", this.networkAddress, apiVersion);
        this.coreClient = new RestClient(safeguardCoreUrl, ignoreSsl);

        this.clientCertificate = new CertificateContext(certificateAlias, certificatePath, certificatePassword);
        this.ignoreSsl = ignoreSsl;
        this.apiVersion = apiVersion;
    }

    public SafeguardA2AContext(String networkAddress, String certificateAlias, int apiVersion, boolean ignoreSsl) {
        this(networkAddress, certificateAlias, null, null, apiVersion, ignoreSsl);
    }
    
    public SafeguardA2AContext(String networkAddress, String certificatePath, char[] certificatePassword,
            int apiVersion, boolean ignoreSsl) {
        this(networkAddress, null, certificatePath, certificatePassword, apiVersion, ignoreSsl);
    }

    @Override
    public List<A2ARetrievableAccount> getRetrievableAccounts()  throws ObjectDisposedException, SafeguardForJavaException {
        
        if (disposed) {
            throw new ObjectDisposedException("SafeguardA2AContext");
        }

        List<A2ARetrievableAccount> list = new ArrayList<>();

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");

        Map<String, String> parameters = new HashMap<>();
        
        CloseableHttpResponse response = coreClient.execGET("A2ARegistrations", parameters, headers, clientCertificate.getCertificatePath(), clientCertificate.getCertificatePassword());

        if (response == null) {
            throw new SafeguardForJavaException(String.format("Unable to connect to web service %s", a2AClient.getBaseURL()));
        }
        
        String reply = Utils.getResponse(response);
        if (!Utils.isSuccessful(response.getStatusLine().getStatusCode())) 
            throw new SafeguardForJavaException(String.format("Error returned from Safeguard API, Error: %s %s", response.getStatusLine().getStatusCode(), reply));
        
        
        List<A2ARegistration> registrations = parseA2ARegistationResponse(reply);
        
        for (A2ARegistration registration : registrations) {
            
            int registrationId = registration.getId();
            
            response = coreClient.execGET(String.format("A2ARegistrations/%d/RetrievableAccounts", registrationId), 
                    parameters, headers, clientCertificate.getCertificatePath(), clientCertificate.getCertificatePassword());
            
            if (response == null) {
                throw new SafeguardForJavaException(String.format("Unable to connect to web service %s", a2AClient.getBaseURL()));
            }
            
            reply = Utils.getResponse(response);
            if (!Utils.isSuccessful(response.getStatusLine().getStatusCode())) 
                throw new SafeguardForJavaException(String.format("Error returned from Safeguard API, Error: %s %s", response.getStatusLine().getStatusCode(), reply));
        
            List<A2ARetrievableAccountInternal> retrievals = parseA2ARetrievableAccountResponse(reply);
            
            for (A2ARetrievableAccountInternal retrieval : retrievals)
            {
                A2ARetrievableAccount account = new A2ARetrievableAccount();
                account.setApplicationName(registration.getAppName());
                account.setDescription(registration.getDescription());
                account.setDisabled(registration.isDisabled() || retrieval.isAccountDisabled());
                account.setAccountId(retrieval.getAccountId());
                account.setApiKey(retrieval.getApiKey().toCharArray());
                account.setAssetId(retrieval.getSystemId());
                account.setAssetName(retrieval.getSystemName());
                account.setAssetDescription(retrieval.getAssetDescription());
                account.setAccountId(retrieval.getAccountId());
                account.setAccountName(retrieval.getAccountName());
                account.setDomainName(retrieval.getDomainName());
                account.setAccountType(retrieval.getAccountType());
                account.setAccountDescription(retrieval.getAccountDescription());
                
                list.add(account);
            }
        }
        return list;
    }

    @Override
    public char[] retrievePassword(char[] apiKey) throws ObjectDisposedException, SafeguardForJavaException, ArgumentException {
        
        if (disposed) {
            throw new ObjectDisposedException("SafeguardA2AContext");
        }
        
        if (apiKey == null) {
            throw new ArgumentException("The apiKey parameter may not be null");
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", String.format("A2A %s", new String(apiKey)));

        Map<String, String> parameters = new HashMap<>();
        parameters.put("type", "Password");

        CloseableHttpResponse response = a2AClient.execGET("Credentials", parameters, headers, clientCertificate.getCertificatePath(), clientCertificate.getCertificatePassword());

        if (response == null) {
            throw new SafeguardForJavaException(String.format("Unable to connect to web service %s", a2AClient.getBaseURL()));
        }
        
        String reply = Utils.getResponse(response);
        if (!Utils.isSuccessful(response.getStatusLine().getStatusCode())) {
            throw new SafeguardForJavaException("Error returned from Safeguard API, Error: "
                    + String.format("%s %s", response.getStatusLine().getStatusCode(), reply));
        }

        char[] password = reply.replaceAll("\"", "").toCharArray();
        return password;
    }

    @Override
    public ISafeguardEventListener getA2AEventListener(char[] apiKey, ISafeguardEventHandler handler)
            throws ObjectDisposedException, ArgumentException {
        
        if (disposed) {
            throw new ObjectDisposedException("SafeguardA2AContext");
        }
        if (apiKey == null) {
            throw new ArgumentException("The apiKey parameter may not be null");
        }

        SafeguardEventListener eventListener = new SafeguardEventListener(String.format("https://%s/service/a2a", networkAddress),
                clientCertificate.getCertificatePath(), clientCertificate.getCertificatePassword(), clientCertificate.getCertificateAlias(), apiKey, ignoreSsl);
        eventListener.registerEventHandler("AssetAccountPasswordUpdated", handler);
        Logger.getLogger(SafeguardA2AContext.class.getName()).log(Level.FINEST, "Event listener successfully created for Safeguard A2A context.");
        return eventListener;
    }
    
    @Override
    public ISafeguardEventListener getA2AEventListener(List<char[]> apiKeys, ISafeguardEventHandler handler)
            throws ObjectDisposedException, ArgumentException {
        
        if (disposed) {
            throw new ObjectDisposedException("SafeguardA2AContext");
        }
        if (apiKeys == null) {
            throw new ArgumentException("The apiKeys parameter may not be null");
        }

        SafeguardEventListener eventListener = new SafeguardEventListener(String.format("https://%s/service/a2a", networkAddress),
                clientCertificate.getCertificatePath(), clientCertificate.getCertificatePassword(), clientCertificate.getCertificateAlias(), apiKeys, ignoreSsl);
        eventListener.registerEventHandler("AssetAccountPasswordUpdated", handler);
        Logger.getLogger(SafeguardA2AContext.class.getName()).log(Level.FINEST, "Event listener successfully created for Safeguard A2A context.");
        return eventListener;
    }

    @Override
    public ISafeguardEventListener getPersistentA2AEventListener(char[] apiKey, ISafeguardEventHandler handler) throws ObjectDisposedException, ArgumentException
    {
        if (disposed) {
            throw new ObjectDisposedException("SafeguardA2AContext");
        }
        if (apiKey == null) {
            throw new ArgumentException("The apiKey parameter may not be null");
        }

        return new PersistentSafeguardA2AEventListener((ISafeguardA2AContext)this.cloneObject(), apiKey, handler);
    }
    
    @Override
    public ISafeguardEventListener getPersistentA2AEventListener(List<char[]> apiKeys, ISafeguardEventHandler handler) throws ObjectDisposedException, ArgumentException
    {
        if (disposed) {
            throw new ObjectDisposedException("SafeguardA2AContext");
        }
        if (apiKeys == null) {
            throw new ArgumentException("The apiKeys parameter may not be null");
        }

        return new PersistentSafeguardA2AEventListener((ISafeguardA2AContext)this.cloneObject(), apiKeys, handler);
    }
    
    @Override
    public String brokerAccessRequest(char[] apiKey, BrokeredAccessRequest accessRequest)
            throws ObjectDisposedException, SafeguardForJavaException, ArgumentException {

        if (disposed) {
            throw new ObjectDisposedException("SafeguardA2AContext");
        }
        if (apiKey == null) {
            throw new ArgumentException("apiKey parameter may not be null");
        }
        if (accessRequest == null) {
            throw new ArgumentException("accessRequest parameter may not be null");
        }
        if (accessRequest.getForUserId() == null && accessRequest.getForUserName() == null) {
            throw new SafeguardForJavaException("You must specify a user to create an access request for");
        }
        if (accessRequest.getAssetId() == null && accessRequest.getAssetName() == null) {
            throw new SafeguardForJavaException("You must specify an asset to create an access request for");
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Authorization", String.format("A2A %s", new String(apiKey)));

        Map<String, String> parameters = new HashMap<>();

        CloseableHttpResponse response = a2AClient.execPOST("AccessRequests", parameters, headers, accessRequest, clientCertificate.getCertificatePath(), 
                clientCertificate.getCertificatePassword(), clientCertificate.getCertificateAlias());

        if (response == null) {
            throw new SafeguardForJavaException(String.format("Unable to connect to web service %s", a2AClient.getBaseURL()));
        }
        
        String reply = Utils.getResponse(response);
        if (!Utils.isSuccessful(response.getStatusLine().getStatusCode())) {
            throw new SafeguardForJavaException("Error returned from Safeguard API, Error: "
                    + String.format("%s %s", response.getStatusLine().getStatusCode(), reply));
        }

        Logger.getLogger(SafeguardA2AContext.class.getName()).log(Level.INFO, "Successfully created A2A access request.");
        return reply;
    }

    @Override
    public void dispose() {
        clientCertificate.dispose();
        disposed = true;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            clientCertificate.dispose();
        } finally {
            disposed = true;
            super.finalize();
        }
    }
    
    public Object cloneObject()
    {
        return !Utils.isNullOrEmpty(clientCertificate.getCertificateAlias())
            ? new SafeguardA2AContext(networkAddress, clientCertificate.getCertificateAlias(), apiVersion, ignoreSsl)
            : new SafeguardA2AContext(networkAddress, clientCertificate.getCertificatePath(), clientCertificate.getCertificatePassword(), apiVersion, ignoreSsl);
    }
    
    private List<A2ARegistration> parseA2ARegistationResponse(String response) {
        
        ObjectMapper mapper = new ObjectMapper();
        
        try {
            A2ARegistration[] registrations = mapper.readValue(response, A2ARegistration[].class);
            return Arrays.asList(registrations);
        } catch (IOException ex) {
            Logger.getLogger(Utils.class.getName()).log(Level.SEVERE, null, ex);
        }

        return null;
    }
    
    private List<A2ARetrievableAccountInternal> parseA2ARetrievableAccountResponse(String response) {
        
        ObjectMapper mapper = new ObjectMapper();
        
        try {
            A2ARetrievableAccountInternal[] accounts = mapper.readValue(response, A2ARetrievableAccountInternal[].class);
            return Arrays.asList(accounts);
        } catch (IOException ex) {
            Logger.getLogger(Utils.class.getName()).log(Level.SEVERE, null, ex);
        }

        return null;
    }

}
