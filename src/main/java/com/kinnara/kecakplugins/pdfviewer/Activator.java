package com.kinnara.kecakplugins.pdfviewer;

import java.util.ArrayList;
import java.util.Collection;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();

        //Register plugin here
        registrationList.add(context.registerService(PdfViewerElement.class.getName(), new PdfViewerElement(), null));
        registrationList.add(context.registerService(PdfViewerMenu.class.getName(), new PdfViewerMenu(), null));
        registrationList.add(context.registerService(PdfViewerWebService.class.getName(), new PdfViewerWebService(), null));
    }

    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}