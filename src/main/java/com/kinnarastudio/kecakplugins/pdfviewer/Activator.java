package com.kinnarastudio.kecakplugins.pdfviewer;

import java.util.ArrayList;
import java.util.Collection;

import com.kinnarastudio.kecakplugins.pdfviewer.app.PdfViewerWebService;
import com.kinnarastudio.kecakplugins.pdfviewer.form.PdfUploadElement;
import com.kinnarastudio.kecakplugins.pdfviewer.form.PdfViewerElement;
import com.kinnarastudio.kecakplugins.pdfviewer.userview.PdfUploadMenu;
import com.kinnarastudio.kecakplugins.pdfviewer.userview.PdfViewerMenu;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();

        //Register plugin here
        registrationList.add(context.registerService(PdfViewerElement.class.getName(), new PdfViewerElement(), null));
        registrationList.add(context.registerService(PdfUploadElement.class.getName(), new PdfUploadElement(), null));
        registrationList.add(context.registerService(PdfViewerMenu.class.getName(), new PdfViewerMenu(), null));
        registrationList.add(context.registerService(PdfUploadMenu.class.getName(), new PdfUploadMenu(), null));
        registrationList.add(context.registerService(PdfViewerWebService.class.getName(), new PdfViewerWebService(), null));
    }

    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}