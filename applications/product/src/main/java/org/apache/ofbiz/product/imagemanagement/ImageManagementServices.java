/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.apache.ofbiz.product.imagemanagement;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.ImagingOpException;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.commons.imaging.ImageReadException;
import org.apache.ofbiz.base.location.FlexibleLocation;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.base.util.string.FlexibleStringExpander;
import org.apache.ofbiz.common.image.ImageTransform;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ModelService;
import org.apache.ofbiz.service.ServiceUtil;
import org.jdom2.JDOMException;

/**
 * Product Services
 */
public class ImageManagementServices {

    private static final String MODULE = ImageManagementServices.class.getName();
    private static final String RES_ERROR = "ProductErrorUiLabels";
    private static final String RESOURCE = "ProductUiLabels";
    private static int imageCount = 0;
    private static String imagePath;

    public static Map<String, Object> addMultipleuploadForProduct(DispatchContext dctx,
            Map<String, ? extends Object> context) throws ImageReadException {

        Map<String, Object> result = new HashMap<>();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dctx.getDelegator();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        String productId = (String) context.get("productId");
        productId = productId.trim();
        String productContentTypeId = (String) context.get("productContentTypeId");
        ByteBuffer imageData = (ByteBuffer) context.get("uploadedFile");
        String uploadFileName = (String) context.get("_uploadedFile_fileName");
        String imageResize = (String) context.get("imageResize");
        Locale locale = (Locale) context.get("locale");

        if (UtilValidate.isNotEmpty(uploadFileName)) {
            String imageServerPath = FlexibleStringExpander.expandString(EntityUtilProperties.getPropertyValue("catalog",
                    "image.management.path", delegator), context);
            String imageServerUrl = FlexibleStringExpander.expandString(EntityUtilProperties.getPropertyValue("catalog",
                    "image.management.url", delegator), context);
            String rootTargetDirectory = imageServerPath;
            File rootTargetDir = new File(rootTargetDirectory);
            if (!rootTargetDir.exists()) {
                boolean created = rootTargetDir.mkdirs();
                if (!created) {
                    String errMsg = UtilProperties.getMessage(RES_ERROR, "ProductCannotCreateTheTargetDirectory", locale);
                    Debug.logFatal(errMsg, MODULE);
                    return ServiceUtil.returnError(errMsg);
                }
            }

            String sizeType = null;
            if (UtilValidate.isNotEmpty(imageResize)) {
                sizeType = imageResize;
            }

            Map<String, Object> contentCtx = new HashMap<>();
            contentCtx.put("contentTypeId", "DOCUMENT");
            contentCtx.put("userLogin", userLogin);
            Map<String, Object> contentResult;
            try {
                contentResult = dispatcher.runSync("createContent", contentCtx);
                if (ServiceUtil.isError(contentResult)) {
                    return ServiceUtil.returnError(ServiceUtil.getErrorMessage(contentResult));
                }
            } catch (GenericServiceException e) {
                Debug.logError(e, MODULE);
                return ServiceUtil.returnError(e.getMessage());
            }

            String contentId = (String) contentResult.get("contentId");
            result.put("contentFrameId", contentId);
            result.put("contentId", contentId);

            String fileContentType = (String) context.get("_uploadedFile_contentType");
            if ("image/pjpeg".equals(fileContentType)) {
                fileContentType = "image/jpeg";
            } else if ("image/x-png".equals(fileContentType)) {
                fileContentType = "image/png";
            }

            // Create folder product id.
            String targetDirectory = imageServerPath + "/" + productId;
            File targetDir = new File(targetDirectory);
            if (!targetDir.exists()) {
                boolean created = targetDir.mkdirs();
                if (!created) {
                    String errMsg = "Cannot create the target directory";
                    Debug.logFatal(errMsg, MODULE);
                    return ServiceUtil.returnError(errMsg);
                }
            }

            String fileToCheck = imageServerPath + "/" + productId + "/" + uploadFileName;
            File file = new File(fileToCheck);
            String imageName = null;
            imagePath = imageServerPath + "/" + productId + "/" + uploadFileName;
            file = checkExistsImage(file);
            if (UtilValidate.isNotEmpty(file)) {
                imageName = file.getPath();
                imageName = imageName.substring(imageName.lastIndexOf(File.separator) + 1);
            } else {
                imageName = "";
            }

            if (UtilValidate.isEmpty(imageResize)) {
                try {
                    Path tempFile = Files.createTempFile(null, null);
                    Files.write(tempFile, imageData.array(), StandardOpenOption.APPEND);
                    // Check if a webshell is not uploaded
                    if (!org.apache.ofbiz.security.SecuredUpload.isValidFile(tempFile.toString(), "Image", delegator)) {
                        String errorMessage = UtilProperties.getMessage("SecurityUiLabels", "SupportedImageFormats", locale);
                        return ServiceUtil.returnError(errorMessage);
                    }
                    File tempFileToDelete = new File(tempFile.toString());
                    tempFileToDelete.deleteOnExit();
                    // Create image file original to folder product id.
                    RandomAccessFile out = new RandomAccessFile(file, "rw");
                    out.write(imageData.array());
                    out.close();
                } catch (FileNotFoundException e) {
                    Debug.logError(e, MODULE);
                    return ServiceUtil.returnError(UtilProperties.getMessage(RES_ERROR,
                            "ProductImageViewUnableWriteFile", UtilMisc.toMap("fileName", file.getAbsolutePath()), locale));
                } catch (IOException e) {
                    Debug.logError(e, MODULE);
                    return ServiceUtil.returnError(UtilProperties.getMessage(RES_ERROR,
                            "ProductImageViewUnableWriteBinaryData", UtilMisc.toMap("fileName", file.getAbsolutePath()), locale));
                }
            }
            // Scale Image in different sizes
            if (UtilValidate.isNotEmpty(imageResize)) {
                fileToCheck = imageServerPath + "/" + productId + "/" + imageName;
                File fileOriginal = new File(fileToCheck);
                fileOriginal = checkExistsImage(fileOriginal);

                try {
                    Path tempFile = Files.createTempFile(null, null);
                    Files.write(tempFile, imageData.array(), StandardOpenOption.APPEND);
                    // Check if a webshell is not uploaded
                    if (!org.apache.ofbiz.security.SecuredUpload.isValidFile(tempFile.toString(), "Image", delegator)) {
                        String errorMessage = UtilProperties.getMessage("SecurityUiLabels", "SupportedImageFormats", locale);
                        return ServiceUtil.returnError(errorMessage);
                    }
                    File tempFileToDelete = new File(tempFile.toString());
                    tempFileToDelete.deleteOnExit();
                    RandomAccessFile outFile = new RandomAccessFile(fileOriginal, "rw");
                    outFile.write(imageData.array());
                    outFile.close();
                } catch (FileNotFoundException e) {
                    Debug.logError(e, MODULE);
                    return ServiceUtil.returnError(UtilProperties.getMessage(RES_ERROR,
                            "ProductImageViewUnableWriteFile", UtilMisc.toMap("fileName", fileOriginal.getAbsolutePath()), locale));
                } catch (IOException e) {
                    Debug.logError(e, MODULE);
                    return ServiceUtil.returnError(UtilProperties.getMessage(RES_ERROR,
                            "ProductImageViewUnableWriteBinaryData", UtilMisc.toMap("fileName", fileOriginal.getAbsolutePath()), locale));
                }

                Map<String, Object> resultResize = new HashMap<>();
                try {
                    resultResize.putAll(scaleImageMangementInAllSize(dctx, context, imageName, sizeType, productId));
                } catch (IOException e) {
                    String errMsg = UtilProperties.getMessage(RES_ERROR,
                            "ProductScaleAdditionalImageInAllDifferentSizesIsImpossible", UtilMisc.toMap("errorString", e.toString()), locale);
                    Debug.logError(e, errMsg, MODULE);
                    return ServiceUtil.returnError(errMsg);
                } catch (JDOMException e) {
                    String errMsg = UtilProperties.getMessage(RES_ERROR,
                            "ProductErrorsOccurInParsingImageProperties.xml", UtilMisc.toMap("errorString", e .toString()), locale);
                    Debug.logError(e, errMsg, MODULE);
                    return ServiceUtil.returnError(errMsg);
                }
            }

            Map<String, Object> contentThumbnail = createContentThumbnail(dctx, context, userLogin, imageData, productId, imageName);
            String filenameToUseThumb = (String) contentThumbnail.get("filenameToUseThumb");
            String contentIdThumb = (String) contentThumbnail.get("contentIdThumb");

            String imageUrl = imageServerUrl + "/" + productId + "/" + imageName;
            String imageUrlThumb = imageServerUrl + "/" + productId + "/" + filenameToUseThumb;

            createContentAndDataResource(dctx, userLogin, imageName, imageUrl, contentId, fileContentType);
            createContentAndDataResource(dctx, userLogin, filenameToUseThumb, imageUrlThumb, contentIdThumb, fileContentType);

            Map<String, Object> createContentAssocMap = new HashMap<>();
            createContentAssocMap.put("contentAssocTypeId", "IMAGE_THUMBNAIL");
            createContentAssocMap.put("contentId", contentId);
            createContentAssocMap.put("contentIdTo", contentIdThumb);
            createContentAssocMap.put("userLogin", userLogin);
            createContentAssocMap.put("mapKey", "100");
            try {
                Map<String, Object> serviceResult = dispatcher.runSync("createContentAssoc", createContentAssocMap);
                if (ServiceUtil.isError(serviceResult)) {
                    return ServiceUtil.returnError(ServiceUtil.getErrorMessage(serviceResult));
                }
            } catch (GenericServiceException e) {
                Debug.logError(e, MODULE);
                return ServiceUtil.returnError(e.getMessage());
            }

            Map<String, Object> productContentCtx = new HashMap<>();
            productContentCtx.put("productId", productId);
            productContentCtx.put("productContentTypeId", productContentTypeId);
            productContentCtx.put("fromDate", UtilDateTime.nowTimestamp());
            productContentCtx.put("userLogin", userLogin);
            productContentCtx.put("contentId", contentId);
            productContentCtx.put("statusId", "IM_PENDING");
            try {
                Map<String, Object> serviceResult = dispatcher.runSync("createProductContent", productContentCtx);
                if (ServiceUtil.isError(serviceResult)) {
                    return ServiceUtil.returnError(ServiceUtil.getErrorMessage(serviceResult));
                }
            } catch (GenericServiceException e) {
                Debug.logError(e, MODULE);
                return ServiceUtil.returnError(e.getMessage());
            }

            Map<String, Object> contentApprovalCtx = new HashMap<>();
            contentApprovalCtx.put("contentId", contentId);
            contentApprovalCtx.put("userLogin", userLogin);
            try {
                Map<String, Object> serviceResult = dispatcher.runSync("createImageContentApproval", contentApprovalCtx);
                if (ServiceUtil.isError(serviceResult)) {
                    return ServiceUtil.returnError(ServiceUtil.getErrorMessage(serviceResult));
                }
            } catch (GenericServiceException e) {
                Debug.logError(e, MODULE);
                return ServiceUtil.returnError(e.getMessage());
            }

            String autoApproveImage = EntityUtilProperties.getPropertyValue("catalog", "image.management.autoApproveImage", delegator);
            if ("Y".equals(autoApproveImage)) {
                Map<String, Object> autoApproveCtx = new HashMap<>();
                autoApproveCtx.put("contentId", contentId);
                autoApproveCtx.put("userLogin", userLogin);
                autoApproveCtx.put("checkStatusId", "IM_APPROVED");
                try {
                    Map<String, Object> serviceResult = dispatcher.runSync("updateStatusImageManagement", autoApproveCtx);
                    if (ServiceUtil.isError(serviceResult)) {
                        return ServiceUtil.returnError(ServiceUtil.getErrorMessage(serviceResult));
                    }
                } catch (GenericServiceException e) {
                    Debug.logError(e, MODULE);
                    return ServiceUtil.returnError(e.getMessage());
                }
            }
        }
        return result;
    }

    public static Map<String, Object> removeImageFileForImageManagement(DispatchContext dctx, Map<String, ? extends Object> context) {
        String productId = (String) context.get("productId");
        String contentId = (String) context.get("contentId");
        String dataResourceName = (String) context.get("dataResourceName");
        Delegator delegator = dctx.getDelegator();

        try {
            if (UtilValidate.isNotEmpty(contentId)) {
                String imageServerPath = FlexibleStringExpander.expandString(EntityUtilProperties.getPropertyValue("catalog",
                        "image.management.path", delegator), context);
                File file = new File(imageServerPath + "/" + productId + "/" + dataResourceName);
                if (!file.delete()) {
                    Debug.logError("File :" + file.getName() + ", couldn't be deleted", MODULE);
                }
            }
        } catch (Exception e) {
            return ServiceUtil.returnError(e.getMessage());
        }
        return ServiceUtil.returnSuccess();
    }

    private static Map<String, Object> scaleImageMangementInAllSize(DispatchContext dctx, Map<String, ? extends Object> context,
                                                                    String filenameToUse, String resizeType, String productId)
        throws IllegalArgumentException, ImagingOpException, IOException, JDOMException {

        /* VARIABLES */
        Locale locale = (Locale) context.get("locale");
        List<String> sizeTypeList = null;
        if (UtilValidate.isNotEmpty(resizeType)) {
            sizeTypeList = UtilMisc.toList(resizeType);
        } else {
            sizeTypeList = UtilMisc.toList("small", "100x75", "150x112", "320x240", "640x480", "800x600", "1024x768", "1280x1024", "1600x1200");
        }

        int index;
        Map<String, Map<String, String>> imgPropertyMap = new HashMap<>();
        BufferedImage bufImg;
        BufferedImage bufNewImg;
        double imgHeight;
        double imgWidth;
        Map<String, String> imgUrlMap = new HashMap<>();
        Map<String, Object> resultXMLMap = new HashMap<>();
        Map<String, Object> resultBufImgMap = new HashMap<>();
        Map<String, Object> resultScaleImgMap = new HashMap<>();
        Map<String, Object> result = new HashMap<>();

        /* ImageProperties.xml */
        String fileName = "component://product/config/ImageProperties.xml";
        String imgPropertyFullPath = FlexibleLocation.resolveLocation(fileName).getFile();
        resultXMLMap.putAll(ImageTransform.getXMLValue(imgPropertyFullPath, locale));
        if (resultXMLMap.containsKey("responseMessage") && "success".equals(resultXMLMap.get("responseMessage"))) {
            imgPropertyMap.putAll(UtilGenerics.<Map<String, Map<String, String>>>cast(resultXMLMap.get("xml")));
        } else {
            String errMsg = UtilProperties.getMessage(RES_ERROR, "ScaleImage.unable_to_parse", locale) + " : ImageProperties.xml";
            Debug.logError(errMsg, MODULE);
            result.put(ModelService.ERROR_MESSAGE, errMsg);
            return result;
        }

        /* IMAGE */
        // get Name and Extension
        index = filenameToUse.lastIndexOf('.');
        String imgExtension = filenameToUse.substring(index + 1);
        // paths
        String imageServerPath = FlexibleStringExpander.expandString(EntityUtilProperties.getPropertyValue("catalog",
                "image.management.path", dctx.getDelegator()), context);
        String imageServerUrl = FlexibleStringExpander.expandString(EntityUtilProperties.getPropertyValue("catalog",
                "image.management.url", dctx.getDelegator()), context);


        /* get original BUFFERED IMAGE */
        resultBufImgMap.putAll(ImageTransform.getBufferedImage(imageServerPath + "/" + productId + "/" + filenameToUse, locale));

        if (resultBufImgMap.containsKey("responseMessage") && "success".equals(resultBufImgMap.get("responseMessage"))) {
            bufImg = (BufferedImage) resultBufImgMap.get("bufferedImage");

            // get Dimensions
            imgHeight = bufImg.getHeight();
            imgWidth = bufImg.getWidth();
            if (imgHeight == 0.0 || imgWidth == 0.0) {
                String errMsg = UtilProperties.getMessage(RES_ERROR, "ScaleImage.one_current_image_dimension_is_null", locale) + " : imgHeight = "
                        + imgHeight + " ; imgWidth = " + imgWidth;
                Debug.logError(errMsg, MODULE);
                result.put(ModelService.ERROR_MESSAGE, errMsg);
                return result;
            }

            /* scale Image for each Size Type */
            for (String sizeType : sizeTypeList) {
                resultScaleImgMap.putAll(ImageTransform.scaleImage(bufImg, imgHeight, imgWidth, imgPropertyMap, sizeType, locale));

                if (resultScaleImgMap.containsKey("responseMessage") && "success".equals(resultScaleImgMap.get("responseMessage"))) {
                    bufNewImg = (BufferedImage) resultScaleImgMap.get("bufferedImage");

                    // write the New Scaled Image

                    String targetDirectory = imageServerPath + "/" + productId;
                    File targetDir = new File(targetDirectory);
                    if (!targetDir.exists()) {
                        boolean created = targetDir.mkdirs();
                        if (!created) {
                            String errMsg = UtilProperties.getMessage(RES_ERROR, "ScaleImage.unable_to_create_target_directory", locale)
                                    + " - " + targetDirectory;
                            Debug.logFatal(errMsg, MODULE);
                            return ServiceUtil.returnError(errMsg);
                        }
                    }

                    // write new image
                    try {
                        ImageIO.write(bufNewImg, imgExtension, new File(imageServerPath + "/" + productId + "/" + filenameToUse));
                        File deleteFile = new File(imageServerPath + "/" + filenameToUse);
                        if (!deleteFile.delete()) {
                            Debug.logError("File :" + deleteFile.getName() + ", couldn't be deleted", MODULE);
                        }
                    } catch (IllegalArgumentException e) {
                        String errMsg = UtilProperties.getMessage(RES_ERROR, "ScaleImage.one_parameter_is_null", locale) + e.toString();
                        Debug.logError(errMsg, MODULE);
                        result.put(ModelService.ERROR_MESSAGE, errMsg);
                        return result;
                    } catch (IOException e) {
                        String errMsg = UtilProperties.getMessage(RES_ERROR, "ScaleImage.error_occurs_during_writing", locale) + e.toString();
                        Debug.logError(errMsg, MODULE);
                        result.put(ModelService.ERROR_MESSAGE, errMsg);
                        return result;
                    }

                    /* write Return Result */
                    String imageUrl = imageServerUrl + "/" + productId + "/" + filenameToUse;
                    imgUrlMap.put(sizeType, imageUrl);

                } // scaleImgMap
            } // sizeIter

            result.put("responseMessage", "success");
            result.put("imageUrlMap", imgUrlMap);
            result.put("original", resultBufImgMap);
            return result;

        }
        String errMsg = UtilProperties.getMessage(RES_ERROR, "ScaleImage.unable_to_scale_original_image", locale)
                + " : " + filenameToUse;
        Debug.logError(errMsg, MODULE);
        result.put(ModelService.ERROR_MESSAGE, errMsg);
        return ServiceUtil.returnError(errMsg);
    }

    public static Map<String, Object> createContentAndDataResource(DispatchContext dctx, GenericValue userLogin, String filenameToUse,
                                                                   String imageUrl, String contentId, String fileContentType) {
        Map<String, Object> result = new HashMap<>();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dctx.getDelegator();

        Map<String, Object> dataResourceCtx = new HashMap<>();

        dataResourceCtx.put("objectInfo", imageUrl);
        dataResourceCtx.put("dataResourceName", filenameToUse);
        dataResourceCtx.put("userLogin", userLogin);
        dataResourceCtx.put("dataResourceTypeId", "IMAGE_OBJECT");
        dataResourceCtx.put("mimeTypeId", fileContentType);
        dataResourceCtx.put("isPublic", "Y");

        Map<String, Object> dataResourceResult;
        try {
            dataResourceResult = dispatcher.runSync("createDataResource", dataResourceCtx);
            if (ServiceUtil.isError(dataResourceResult)) {
                return ServiceUtil.returnError(ServiceUtil.getErrorMessage(dataResourceResult));
            }
        } catch (GenericServiceException e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError(e.getMessage());
        }

        String dataResourceId = (String) dataResourceResult.get("dataResourceId");
        result.put("dataResourceFrameId", dataResourceId);
        result.put("dataResourceId", dataResourceId);

        Map<String, Object> contentUp = new HashMap<>();
        contentUp.put("contentId", contentId);
        contentUp.put("dataResourceId", dataResourceResult.get("dataResourceId"));
        contentUp.put("contentName", filenameToUse);
        contentUp.put("userLogin", userLogin);
        try {
            Map<String, Object> serviceResult = dispatcher.runSync("updateContent", contentUp);
            if (ServiceUtil.isError(serviceResult)) {
                return ServiceUtil.returnError(ServiceUtil.getErrorMessage(serviceResult));
            }
        } catch (GenericServiceException e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError(e.getMessage());
        }

        GenericValue content = null;
        try {
            content = EntityQuery.use(delegator).from("Content").where("contentId", contentId).queryOne();
        } catch (GenericEntityException e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError(e.getMessage());
        }

        if (content != null) {
            GenericValue dataResource = null;
            try {
                dataResource = content.getRelatedOne("DataResource", false);
            } catch (GenericEntityException e) {
                Debug.logError(e, MODULE);
                return ServiceUtil.returnError(e.getMessage());
            }

            if (dataResource != null) {
                dataResourceCtx.put("dataResourceId", dataResource.getString("dataResourceId"));
                try {
                    Map<String, Object> serviceResult = dispatcher.runSync("updateDataResource", dataResourceCtx);
                    if (ServiceUtil.isError(serviceResult)) {
                        return ServiceUtil.returnError(ServiceUtil.getErrorMessage(serviceResult));
                    }
                } catch (GenericServiceException e) {
                    Debug.logError(e, MODULE);
                    return ServiceUtil.returnError(e.getMessage());
                }
            }
        }
        return result;
    }

    public static Map<String, Object> createContentThumbnail(DispatchContext dctx, Map<String, ? extends Object> context,
            GenericValue userLogin, ByteBuffer imageData, String productId, String imageName) throws ImageReadException {
        Map<String, Object> result = new HashMap<>();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dctx.getDelegator();
        Locale locale = (Locale) context.get("locale");
        String imageServerPath = FlexibleStringExpander.expandString(EntityUtilProperties.getPropertyValue("catalog",
                "image.management.path", delegator), context);
        String nameOfThumb = FlexibleStringExpander.expandString(EntityUtilProperties.getPropertyValue("catalog",
                "image.management.nameofthumbnail", delegator), context);

        // Create content for thumbnail
        Map<String, Object> contentThumb = new HashMap<>();
        contentThumb.put("contentTypeId", "DOCUMENT");
        contentThumb.put("userLogin", userLogin);
        Map<String, Object> contentThumbResult;
        try {
            contentThumbResult = dispatcher.runSync("createContent", contentThumb);
            if (ServiceUtil.isError(contentThumbResult)) {
                return ServiceUtil.returnError(ServiceUtil.getErrorMessage(contentThumbResult));
            }
        } catch (GenericServiceException e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError(e.getMessage());
        }

        String contentIdThumb = (String) contentThumbResult.get("contentId");
        result.put("contentIdThumb", contentIdThumb);
        String filenameToUseThumb = imageName.substring(0, imageName.indexOf('.')) + nameOfThumb;
        String fileContentType = (String) context.get("_uploadedFile_contentType");
        if ("image/pjpeg".equals(fileContentType)) {
            fileContentType = "image/jpeg";
        } else if ("image/x-png".equals(fileContentType)) {
            fileContentType = "image/png";
        }

        List<GenericValue> fileExtensionThumb;
        try {
            fileExtensionThumb = EntityQuery.use(delegator).from("FileExtension").where("mimeTypeId", fileContentType).queryList();
        } catch (GenericEntityException e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError(e.getMessage());
        }

        GenericValue extensionThumb = EntityUtil.getFirst(fileExtensionThumb);
        if (extensionThumb != null) {
            filenameToUseThumb += "." + extensionThumb.getString("fileExtensionId");
        }
        result.put("filenameToUseThumb", filenameToUseThumb);
        // Create image file thumbnail to folder product id.
        String fileToCheck = imageServerPath + "/" + productId + "/" + filenameToUseThumb;
        File fileOriginalThumb = new File(fileToCheck);
        try {
            Path tempFile = Files.createTempFile(null, null);
            Files.write(tempFile, imageData.array(), StandardOpenOption.APPEND);
            // Check if a webshell is not uploaded
            if (!org.apache.ofbiz.security.SecuredUpload.isValidFile(tempFile.toString(), "Image", delegator)) {
                String errorMessage = UtilProperties.getMessage("SecurityUiLabels", "SupportedImageFormats", locale);
                return ServiceUtil.returnError(errorMessage);
            }
            File tempFileToDelete = new File(tempFile.toString());
            tempFileToDelete.deleteOnExit();
            RandomAccessFile outFileThumb = new RandomAccessFile(fileOriginalThumb, "rw");
            outFileThumb.write(imageData.array());
            outFileThumb.close();
        } catch (FileNotFoundException e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError(UtilProperties.getMessage(RES_ERROR,
                    "ProductImageViewUnableWriteFile",
                    UtilMisc.toMap("fileName", fileOriginalThumb.getAbsolutePath()), locale));
        } catch (IOException e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError(UtilProperties.getMessage(RES_ERROR,
                    "ProductImageViewUnableWriteBinaryData",
                    UtilMisc.toMap("fileName", fileOriginalThumb.getAbsolutePath()), locale));
        }

        return result;
    }

    public static Map<String, Object> resizeImageThumbnail(BufferedImage bufImg, double imgHeight, double imgWidth) {

        /* VARIABLES */
        BufferedImage bufNewImg;
        double defaultHeight;
        double defaultWidth;
        double scaleFactor;
        Map<String, Object> result = new HashMap<>();

        /* DIMENSIONS from ImageProperties */
        defaultHeight = 100;
        defaultWidth = 100;

        /* SCALE FACTOR */
        // find the right Scale Factor related to the Image Dimensions
        if (imgHeight > imgWidth) {
            scaleFactor = defaultHeight / imgHeight;

            // get scaleFactor from the smallest width
            if (defaultWidth < (imgWidth * scaleFactor)) {
                scaleFactor = defaultWidth / imgWidth;
            }
        } else {
            scaleFactor = defaultWidth / imgWidth;
            // get scaleFactor from the smallest height
            if (defaultHeight < (imgHeight * scaleFactor)) {
                scaleFactor = defaultHeight / imgHeight;
            }
        }

        int bufImgType;
        if (BufferedImage.TYPE_CUSTOM == bufImg.getType()) {
            // apply a type for image majority
            bufImgType = BufferedImage.TYPE_INT_ARGB_PRE;
        } else {
            bufImgType = bufImg.getType();
        }

        // scale original image with new size
        Image newImg = bufImg.getScaledInstance((int) (imgWidth * scaleFactor), (int) (imgHeight * scaleFactor), Image.SCALE_SMOOTH);

        bufNewImg = ImageTransform.toBufferedImage(newImg, bufImgType);

        result.put("bufferedImage", bufNewImg);
        result.put("scaleFactor", scaleFactor);
        return result;
    }

    public static File checkExistsImage(File file) {
        if (!file.exists()) {
            imageCount = 0;
            imagePath = null;
            return file;
        }
        imageCount++;
        String filePath = imagePath.substring(0, imagePath.lastIndexOf('.'));
        String type = imagePath.substring(imagePath.lastIndexOf('.') + 1);
        file = new File(filePath + "(" + imageCount + ")." + type);
        return checkExistsImage(file);
    }

    public static Map<String, Object> resizeImage(BufferedImage bufImg, double imgHeight, double imgWidth, double resizeHeight, double resizeWidth) {

        /* VARIABLES */
        BufferedImage bufNewImg;
        double defaultHeight;
        double defaultWidth;
        double scaleFactor;
        Map<String, Object> result = new HashMap<>();

        /* DIMENSIONS from ImageProperties */
        defaultHeight = resizeHeight;
        defaultWidth = resizeWidth;

        /* SCALE FACTOR */
        // find the right Scale Factor related to the Image Dimensions
        if (imgHeight > imgWidth) {
            scaleFactor = defaultHeight / imgHeight;

            // get scaleFactor from the smallest width
            if (defaultWidth < (imgWidth * scaleFactor)) {
                scaleFactor = defaultWidth / imgWidth;
            }
        } else {
            scaleFactor = defaultWidth / imgWidth;
            // get scaleFactor from the smallest height
            if (defaultHeight < (imgHeight * scaleFactor)) {
                scaleFactor = defaultHeight / imgHeight;
            }
        }

        int bufImgType;
        if (BufferedImage.TYPE_CUSTOM == bufImg.getType()) {
            // apply a type for image majority
            bufImgType = BufferedImage.TYPE_INT_ARGB_PRE;
        } else {
            bufImgType = bufImg.getType();
        }

        // scale original image with new size
        Image newImg = bufImg.getScaledInstance((int) (imgWidth * scaleFactor), (int) (imgHeight * scaleFactor), Image.SCALE_SMOOTH);

        bufNewImg = ImageTransform.toBufferedImage(newImg, bufImgType);

        result.put("bufferedImage", bufNewImg);
        result.put("scaleFactor", scaleFactor);
        return result;
    }

    public static Map<String, Object> createNewImageThumbnail(DispatchContext dctx, Map<String, ? extends Object> context) {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dispatcher.getDelegator();
        Locale locale = (Locale) context.get("locale");
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        String imageServerPath = FlexibleStringExpander.expandString(EntityUtilProperties.getPropertyValue("catalog",
                "image.management.path", delegator), context);
        String imageServerUrl = FlexibleStringExpander.expandString(EntityUtilProperties.getPropertyValue("catalog",
                "image.management.url", delegator), context);
        String productId = (String) context.get("productId");
        String contentId = (String) context.get("contentId");
        String dataResourceName = (String) context.get("dataResourceName");
        String width = (String) context.get("sizeWidth");
        String imageType = ".jpg";
        int resizeWidth = Integer.parseInt(width);
        int resizeHeight = resizeWidth;

        try {
            BufferedImage bufImg = ImageIO.read(new File(imageServerPath + "/" + productId + "/" + dataResourceName));
            double imgHeight = bufImg.getHeight();
            double imgWidth = bufImg.getWidth();
            if (dataResourceName.lastIndexOf('.') > 0 && dataResourceName.lastIndexOf('.') < dataResourceName.length()) {
                imageType = dataResourceName.substring(dataResourceName.lastIndexOf('.'));
            }

            String filenameToUse = dataResourceName.substring(0, dataResourceName.length() - 4) + "-" + resizeWidth + imageType;

            if (dataResourceName.length() > 3) {
                String mimeType = dataResourceName.substring(dataResourceName.length() - 3, dataResourceName.length());
                Map<String, Object> resultResize = resizeImage(bufImg, imgHeight, imgWidth, resizeHeight, resizeWidth);
                ImageIO.write((RenderedImage) resultResize.get("bufferedImage"), mimeType, new File(imageServerPath
                        + "/" + productId + "/" + filenameToUse));

                Map<String, Object> contentThumb = new HashMap<>();
                contentThumb.put("contentTypeId", "DOCUMENT");
                contentThumb.put("userLogin", userLogin);
                Map<String, Object> contentThumbResult;
                try {
                    contentThumbResult = dispatcher.runSync("createContent", contentThumb);
                    if (ServiceUtil.isError(contentThumbResult)) {
                        return ServiceUtil.returnError(ServiceUtil.getErrorMessage(contentThumbResult));
                    }
                } catch (GenericServiceException e) {
                    Debug.logError(e, MODULE);
                    return ServiceUtil.returnError(e.getMessage());
                }

                String contentIdThumb = (String) contentThumbResult.get("contentId");
                String imageUrlThumb = imageServerUrl + "/" + productId + "/" + filenameToUse;
                createContentAndDataResource(dctx, userLogin, filenameToUse, imageUrlThumb, contentIdThumb, "image/jpeg");

                Map<String, Object> createContentAssocMap = new HashMap<>();
                createContentAssocMap.put("contentAssocTypeId", "IMAGE_THUMBNAIL");
                createContentAssocMap.put("contentId", contentId);
                createContentAssocMap.put("contentIdTo", contentIdThumb);
                createContentAssocMap.put("userLogin", userLogin);
                createContentAssocMap.put("mapKey", width);
                try {
                    Map<String, Object> serviceResult = dispatcher.runSync("createContentAssoc", createContentAssocMap);
                    if (ServiceUtil.isError(serviceResult)) {
                        return ServiceUtil.returnError(ServiceUtil.getErrorMessage(serviceResult));
                    }
                } catch (GenericServiceException e) {
                    Debug.logError(e, MODULE);
                    return ServiceUtil.returnError(e.getMessage());
                }
            }
        } catch (IOException e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError(e.getMessage());
        }
        String successMsg = UtilProperties.getMessage(RESOURCE, "ProductCreateNewThumbnailSizeSuccessful", locale);
        return ServiceUtil.returnSuccess(successMsg);
    }

    public static Map<String, Object> resizeImageOfProduct(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        Locale locale = (Locale) context.get("locale");
        String imageServerPath = FlexibleStringExpander.expandString(EntityUtilProperties.getPropertyValue("catalog",
                "image.management.path", delegator), context);
        String productId = (String) context.get("productId");
        String dataResourceName = (String) context.get("dataResourceName");
        String width = (String) context.get("resizeWidth");
        int resizeWidth = Integer.parseInt(width);
        int resizeHeight = resizeWidth;

        try {
            BufferedImage bufImg = ImageIO.read(new File(imageServerPath + "/" + productId + "/" + dataResourceName));
            double imgHeight = bufImg.getHeight();
            double imgWidth = bufImg.getWidth();
            String filenameToUse = dataResourceName;
            String mimeType = dataResourceName.substring(dataResourceName.length() - 3, dataResourceName.length());
            Map<String, Object> resultResize = resizeImage(bufImg, imgHeight, imgWidth, resizeHeight, resizeWidth);
            ImageIO.write((RenderedImage) resultResize.get("bufferedImage"), mimeType, new File(imageServerPath + "/"
                    + productId + "/" + filenameToUse));
        } catch (Exception e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError(e.getMessage());
        }
        String successMsg = UtilProperties.getMessage(RESOURCE, "ProductResizeImagesSuccessful", locale);
        return ServiceUtil.returnSuccess(successMsg);
    }

    public static Map<String, Object> renameImage(DispatchContext dctx, Map<String, ? extends Object> context) {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dctx.getDelegator();
        Locale locale = (Locale) context.get("locale");
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        String imageServerPath = FlexibleStringExpander.expandString(EntityUtilProperties.getPropertyValue("catalog",
                "image.management.path", delegator), context);
        String imageServerUrl = FlexibleStringExpander.expandString(EntityUtilProperties.getPropertyValue("catalog",
                "image.management.url", delegator), context);
        String productId = (String) context.get("productId");
        String contentId = (String) context.get("contentId");
        String filenameToUse = (String) context.get("drDataResourceName");
        String imageType = filenameToUse.substring(filenameToUse.lastIndexOf('.'));
        String imgExtension = filenameToUse.substring(filenameToUse.length() - 3, filenameToUse.length());
        String imageUrl = imageServerUrl + "/" + productId + "/" + filenameToUse;

        try {
            GenericValue productContent = EntityQuery.use(delegator).from("ProductContentAndInfo").where("productId", productId, "contentId",
                    contentId, "productContentTypeId", "IMAGE").queryFirst();
            String dataResourceName = (String) productContent.get("drDataResourceName");
            String mimeType = filenameToUse.substring(filenameToUse.lastIndexOf('.'));

            if (imageType.equals(mimeType)) {
                BufferedImage bufImg = ImageIO.read(new File(imageServerPath + "/" + productId + "/" + dataResourceName));
                ImageIO.write(bufImg, imgExtension, new File(imageServerPath + "/" + productId + "/" + filenameToUse));

                File file = new File(imageServerPath + "/" + productId + "/" + dataResourceName);
                if (!file.delete()) {
                    Debug.logError("File :" + file.getName() + ", couldn't be deleted", MODULE);
                }

                Map<String, Object> contentUp = new HashMap<>();
                contentUp.put("contentId", contentId);
                contentUp.put("contentName", filenameToUse);
                contentUp.put("userLogin", userLogin);
                try {
                    Map<String, Object> serviceResult = dispatcher.runSync("updateContent", contentUp);
                    if (ServiceUtil.isError(serviceResult)) {
                        return ServiceUtil.returnError(ServiceUtil.getErrorMessage(serviceResult));
                    }
                } catch (GenericServiceException e) {
                    Debug.logError(e, MODULE);
                    return ServiceUtil.returnError(e.getMessage());
                }
                GenericValue content = null;
                try {
                    content = EntityQuery.use(delegator).from("Content").where("contentId", contentId).queryOne();
                } catch (GenericEntityException e) {
                    Debug.logError(e, MODULE);
                    return ServiceUtil.returnError(e.getMessage());
                }
                if (content != null) {
                    GenericValue dataResource = null;
                    try {
                        dataResource = content.getRelatedOne("DataResource", false);
                    } catch (GenericEntityException e) {
                        Debug.logError(e, MODULE);
                        return ServiceUtil.returnError(e.getMessage());
                    }

                    if (dataResource != null) {
                        Map<String, Object> dataResourceCtx = new HashMap<>();
                        dataResourceCtx.put("dataResourceId", dataResource.getString("dataResourceId"));
                        dataResourceCtx.put("objectInfo", imageUrl);
                        dataResourceCtx.put("dataResourceName", filenameToUse);
                        dataResourceCtx.put("userLogin", userLogin);
                        try {
                            Map<String, Object> serviceResult = dispatcher.runSync("updateDataResource", dataResourceCtx);
                            if (ServiceUtil.isError(serviceResult)) {
                                return ServiceUtil.returnError(ServiceUtil.getErrorMessage(serviceResult));
                            }
                        } catch (GenericServiceException e) {
                            Debug.logError(e, MODULE);
                            return ServiceUtil.returnError(e.getMessage());
                        }
                    }
                }

                List<GenericValue> contentAssocList = EntityQuery.use(delegator).from("ContentAssoc").where("contentId", contentId,
                        "contentAssocTypeId", "IMAGE_THUMBNAIL").queryList();
                if (!contentAssocList.isEmpty()) {
                    for (int i = 0; i < contentAssocList.size(); i++) {
                        GenericValue contentAssoc = contentAssocList.get(i);

                        List<GenericValue> dataResourceAssocList = EntityQuery.use(delegator).from("ContentDataResourceView")
                                .where("contentId", contentAssoc.get("contentIdTo")).queryList();
                        GenericValue dataResourceAssoc = EntityUtil.getFirst(dataResourceAssocList);

                        String drDataResourceNameAssoc = (String) dataResourceAssoc.get("drDataResourceName");
                        String filenameToUseAssoc = filenameToUse.substring(0, filenameToUse.length() - 4) + "-" + contentAssoc.get("mapKey")
                                + imageType;
                        String imageUrlAssoc = imageServerUrl + "/" + productId + "/" + filenameToUseAssoc;

                        BufferedImage bufImgAssoc = ImageIO.read(new File(imageServerPath + "/" + productId + "/" + drDataResourceNameAssoc));
                        ImageIO.write(bufImgAssoc, imgExtension, new File(imageServerPath + "/" + productId + "/" + filenameToUseAssoc));

                        File fileAssoc = new File(imageServerPath + "/" + productId + "/" + drDataResourceNameAssoc);
                        if (!fileAssoc.delete()) {
                            Debug.logError("File :" + fileAssoc.getName() + ", couldn't be deleted", MODULE);
                        }

                        Map<String, Object> contentAssocMap = new HashMap<>();
                        contentAssocMap.put("contentId", contentAssoc.get("contentIdTo"));
                        contentAssocMap.put("contentName", filenameToUseAssoc);
                        contentAssocMap.put("userLogin", userLogin);
                        try {
                            Map<String, Object> serviceResult = dispatcher.runSync("updateContent", contentAssocMap);
                            if (ServiceUtil.isError(serviceResult)) {
                                return ServiceUtil.returnError(ServiceUtil.getErrorMessage(serviceResult));
                            }
                        } catch (GenericServiceException e) {
                            Debug.logError(e, MODULE);
                            return ServiceUtil.returnError(e.getMessage());
                        }
                        GenericValue contentAssocUp = null;
                        try {
                            contentAssocUp = EntityQuery.use(delegator).from("Content").where("contentId", contentAssoc.get("contentIdTo"))
                                    .queryOne();
                        } catch (GenericEntityException e) {
                            Debug.logError(e, MODULE);
                            return ServiceUtil.returnError(e.getMessage());
                        }
                        if (contentAssocUp != null) {
                            GenericValue dataResourceAssocUp = null;
                            try {
                                dataResourceAssocUp = contentAssocUp.getRelatedOne("DataResource", false);
                            } catch (GenericEntityException e) {
                                Debug.logError(e, MODULE);
                                return ServiceUtil.returnError(e.getMessage());
                            }

                            if (dataResourceAssocUp != null) {
                                Map<String, Object> dataResourceAssocMap = new HashMap<>();
                                dataResourceAssocMap.put("dataResourceId", dataResourceAssocUp.getString("dataResourceId"));
                                dataResourceAssocMap.put("objectInfo", imageUrlAssoc);
                                dataResourceAssocMap.put("dataResourceName", filenameToUseAssoc);
                                dataResourceAssocMap.put("userLogin", userLogin);
                                try {
                                    Map<String, Object> serviceResult = dispatcher.runSync("updateDataResource", dataResourceAssocMap);
                                    if (ServiceUtil.isError(serviceResult)) {
                                        return ServiceUtil.returnError(ServiceUtil.getErrorMessage(serviceResult));
                                    }
                                } catch (GenericServiceException e) {
                                    Debug.logError(e, MODULE);
                                    return ServiceUtil.returnError(e.getMessage());
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException | IllegalArgumentException | GenericEntityException e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError(e.getMessage());
        }
        String successMsg = UtilProperties.getMessage(RESOURCE, "ProductRenameImageSuccessfully.", locale);
        return ServiceUtil.returnSuccess(successMsg);
    }
}
