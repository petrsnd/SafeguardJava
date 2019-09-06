package com.oneidentity.safeguard.safeguardjava.authentication;

import com.oneidentity.safeguard.safeguardjava.exceptions.SafeguardForJavaException;

public class AnonymousAuthenticator extends AuthenticatorBase {

    private boolean disposed;

    public AnonymousAuthenticator(String networkAddress, int apiVersion, boolean ignoreSsl) {
        super(networkAddress, null, null, apiVersion, ignoreSsl);
    }

    @Override
    public String getId() {
        return "Anonymous";
    }
    
    @Override
    public boolean isAnonymous() {
        return true;
    }
    
    @Override
    protected char[] getRstsTokenInternal() throws SafeguardForJavaException {
        throw new SafeguardForJavaException("Anonymous connection cannot be used to get an API access token, Error: Unsupported operation");
    }

    @Override
    public boolean hasAccessToken() {
        return true;
    }
    
    @Override
    public Object cloneObject() throws SafeguardForJavaException {
        throw new SafeguardForJavaException("Anonymous authenticators are not cloneable");
    }
    
    @Override
    public void dispose() {
        super.dispose();
        disposed = true;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
        } finally {
            disposed = true;
            super.finalize();
        }
    }

}
