package com.backbase.training;

import com.backbase.portal.commons.configuration.BackbaseConfiguration;
import org.apache.camel.Handler;
import org.apache.camel.Header;
import org.apache.camel.component.file.GenericFile;
import org.apache.chemistry.opencmis.client.api.*;
import org.apache.chemistry.opencmis.client.runtime.SessionFactoryImpl;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.enums.BindingType;
import org.apache.chemistry.opencmis.commons.enums.VersioningState;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.ContentStreamImpl;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CmisUploader {

    private static final Logger logger = LoggerFactory.getLogger(CmisUploader.class);

    private Session cmisSession;
    private Map<String, String> cmisConnectionProperties;

    @Autowired
    public CmisUploader(BackbaseConfiguration backbaseConfiguration) {
        cmisConnectionProperties = new HashMap<>();
        cmisConnectionProperties.put(SessionParameter.USER, (String) backbaseConfiguration.getProperty("orchestrator.contenthost.username"));
        cmisConnectionProperties.put(SessionParameter.PASSWORD, (String) backbaseConfiguration.getProperty("orchestrator.contenthost.password"));
        cmisConnectionProperties.put(SessionParameter.ATOMPUB_URL, (String) backbaseConfiguration.getProperty("orchestrator.contenthost.atompath"));
        cmisConnectionProperties.put(SessionParameter.BINDING_TYPE, BindingType.ATOMPUB.value());
        cmisConnectionProperties.put(SessionParameter.REPOSITORY_ID, (String) backbaseConfiguration.getProperty("orchestrator.contenthost.repositoryid"));
        logger.debug("Starting CMIS uploader configured to communicate with: " + backbaseConfiguration.getProperty("orchestrator.contenthost.atompath"));

    }

    //@Handler
    public void upload(GenericFile<File> body, @Header("mimeType") String mimeType) {
        logger.debug("Uploading file: " + body.getFileName());
        String filePath = body.getRelativeFilePath().replace("\\", "/");
        try {
            Document file = (Document) getCmisSession().getObjectByPath(toCmisPath(filePath));
            try (InputStream in = new FileInputStream(body.getFile())) {
                ContentStream contentStream = new ContentStreamImpl(body.getFileNameOnly(), BigInteger.valueOf(body.getFileLength()), mimeType, in);
                file.setContentStream(contentStream, true, true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (CmisObjectNotFoundException e) {
            String parentPath = extractParent(filePath);
            Folder parent = getOrCreateFolder(getCmisSession().getRootFolder(), Arrays.asList(parentPath.split("/")), 1);

            try (InputStream in = new FileInputStream(body.getFile())) {
                ContentStream contentStream = new ContentStreamImpl(body.getFileNameOnly(), BigInteger.valueOf(body.getFileLength()), mimeType, in);
                createDocument(parent, body.getFileNameOnly(), contentStream, mimeType);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

//    You can mark a method in your bean with the @Handler annotation to indicate that this method should be used for
//    Bean Binding.
//    This has an advantage as you need not specify a method name in the Camel route, and therefore do not run into
//    problems after renaming the method in an IDE that can't find all its references.
    @Handler
    public void uploadFlat(GenericFile<File> body, @Header("mimeType") String mimeType) {
        logger.debug("Uploading file: " + body.getFileName());
        try {
            Document file = (Document) getCmisSession().getObjectByPath(toCmisPath(body.getFileNameOnly()));
            try (InputStream in = new FileInputStream(body.getFile())) {
                ContentStream contentStream = new ContentStreamImpl(body.getFileNameOnly(), BigInteger.valueOf(body.getFileLength()), mimeType, in);
                file.setContentStream(contentStream, true, true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (CmisObjectNotFoundException e) {
            Folder parent = getCmisSession().getRootFolder();
            try (InputStream in = new FileInputStream(body.getFile())) {
                ContentStream contentStream = new ContentStreamImpl(body.getFileNameOnly(), BigInteger.valueOf(body.getFileLength()), mimeType, in);
                createDocument(parent, body.getFileNameOnly(), contentStream, mimeType);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private Folder getOrCreateFolder(Folder parent, List<String> pathParts, int depth) {
        String currentPath = toCmisPath(StringUtils.join(pathParts.subList(0, depth), "/"));
        String name = pathParts.get(depth - 1);
        Folder current;
        try {
            current = (Folder) getCmisSession().getObjectByPath(currentPath);
        } catch (CmisObjectNotFoundException e) {
            current = createFolder(parent, name);
        }
        parent = current;
        if (depth == pathParts.size()) {
            return parent;
        } else {
            return getOrCreateFolder(parent, pathParts, ++depth);
        }
    }

    private Folder createFolder(Folder parent, String name) {
        Map<String, Object> props = new HashMap<>();
        props.put(PropertyIds.BASE_TYPE_ID, DocumentType.FOLDER_BASETYPE_ID);
        props.put(PropertyIds.OBJECT_TYPE_ID, DocumentType.FOLDER_BASETYPE_ID);
        props.put(PropertyIds.NAME, name);
        return parent.createFolder(props);
    }

    private Document createDocument(Folder parent, String name, ContentStream contentStream, String mimeType) {
        VersioningState versoningState = VersioningState.NONE;
        Map<String, Object> props = new HashMap<>();
        props.put(PropertyIds.BASE_TYPE_ID, DocumentType.DOCUMENT_BASETYPE_ID);
        props.put(PropertyIds.NAME, name);
        if (StringUtils.isNotBlank(mimeType) && mimeType.startsWith("image")) {
            props.put(PropertyIds.OBJECT_TYPE_ID, "bb:image");
            props.put("bb:title", name);
            versoningState = VersioningState.MAJOR;
        } else {
            props.put(PropertyIds.OBJECT_TYPE_ID, DocumentType.DOCUMENT_BASETYPE_ID);
        }
        return parent.createDocument(props, contentStream, versoningState);
    }

    private synchronized Session getCmisSession() {
        if (cmisSession == null) {
            SessionFactory factory = SessionFactoryImpl.newInstance();
            cmisSession = factory.createSession(cmisConnectionProperties);
        }
        return cmisSession;
    }

    private String toCmisPath(String relativeFilePath) {
        return relativeFilePath.startsWith("/") ? relativeFilePath : "/" + relativeFilePath;
    }

    private String extractParent(String path) {
        int lastSlashIndex = path.lastIndexOf("/");
        return lastSlashIndex == -1 ? "" : path.substring(0, lastSlashIndex);
    }
}
