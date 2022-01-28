package org.egov.filestore.repository.impl;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.commons.io.FilenameUtils;
import org.egov.filestore.domain.model.Artifact;
import org.egov.filestore.domain.model.FileLocation;
import org.egov.filestore.repository.AzureClientFacade;
import org.egov.filestore.repository.CloudFilesManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.microsoft.azure.storage.OperationContext;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.BlobContainerPublicAccessType;
import com.microsoft.azure.storage.blob.BlobRequestOptions;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.blob.ListBlobItem;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@ConditionalOnProperty(value = "isAzureStorageEnabled", havingValue = "true")
public class AzureBlobStorageImpl implements CloudFilesManager {

	private CloudBlobClient azureBlobClient;
	
	@Autowired
	private AzureClientFacade azureFacade;
	
	@Autowired
	private CloudFileMgrUtils util;
	
	@Value("${is.container.fixed}")
	private Boolean isContainerFixed;
	
	@Value("${fixed.container.name}")
	private String fixedContainerName;
	
	@Value("${azure.blob.host}")
	private String azureBlobStorageHost;
	
	@Value("${azure.accountName}")
	private String azureAccountName;
	
	@Value("${azure.accountKey}")
	private String azureAccountKey;
	
	@Value("${image.small}")
	private String _small;

	@Value("${image.medium}")
	private String _medium;

	@Value("${image.large}")
	private String _large;
	
	private static final String TEMP_FILE_PATH_NAME = "TempFolder/localFile/";
	
	/**
	 * Azure specific implementation
	 * 
	 */
	@Override
	public void saveFiles(List<Artifact> artifacts) {
		if(null == azureBlobClient)
			azureBlobClient = azureFacade.getAzureClient();
		
		artifacts.forEach(artifact -> {
			CloudBlobContainer container= null;
			String completeName = artifact.getFileLocation().getFileName();
			int index = completeName.indexOf('/');
			String containerName = completeName.substring(0, index);
			String fileNameWithPath = completeName.substring(index + 1, completeName.length());
			try {
				if(isContainerFixed)
					container = azureBlobClient.getContainerReference(fixedContainerName);
				else
					container = azureBlobClient.getContainerReference(containerName);
				container.createIfNotExists(BlobContainerPublicAccessType.CONTAINER, new BlobRequestOptions(), new OperationContext());	
				if(artifact.getMultipartFile().getContentType().startsWith("image/") && !artifact.getMultipartFile().getContentType().contains("svg")) {
					System.out.println("Content Type : " + artifact.getMultipartFile().getContentType());
					String extension = FilenameUtils.getExtension(artifact.getMultipartFile().getOriginalFilename());
					System.out.println("Extension : " + extension);
					System.out.println("Multipart File : " + artifact.getMultipartFile());
					System.out.println("fileNameWithPath : " + fileNameWithPath);
					Map<String, BufferedImage> mapOfImagesAndPaths = util.createVersionsOfImage(artifact.getMultipartFile(), fileNameWithPath);
					for(String key: mapOfImagesAndPaths.keySet()) {
						upload(container, key, null, mapOfImagesAndPaths.get(key), extension);
						mapOfImagesAndPaths.get(key).flush();
					}
					
				} else {
					upload(container, fileNameWithPath, artifact.getMultipartFile(), null, null);
				}
				for (ListBlobItem blobItem : container.listBlobs())
					log.info("URI of blob is: " + blobItem.getStorageUri().getPrimaryUri());
			}catch(Exception e) {
				log.error("Exceptione while creating the container: ", e);
			}
			
		});			
	}
	
	/**
	 * There's a problem with this implementation: In case of images, we are trying to retrieve 4 different versions of the same file namely - 
	 * small, medium, large and the original. The path stored in the db is the path of the original file only, we are making suitable changes
	 * to that file path by appending some extensions to obtain file paths of the different versions. 
	 * TODO: This has to be fixed, we need to keep track of all these versions by storing their paths in the db separately instead of deriving them.
	 * 
	 * Secondly, once these paths are obtained, their SAS urls are being returned as comma separated values in a single string, this has to change to
	 * list of strings. We aren't taking this up because this will cause high impact on UI.
	 * TODO: Change comma separated string to list of strings and test it with UI once their changes are done.
	 */
	@Override
	public Map<String, String> getFiles(Map<String, String> mapOfIdAndFilePath) {
		if(null == azureBlobClient)
			azureBlobClient = azureFacade.getAzureClient();
		Map<String, String> mapOfIdAndSASUrls = new HashMap<>();
		mapOfIdAndFilePath.keySet().forEach(id -> {
			if(util.isFileAnImage(mapOfIdAndFilePath.get(id))) {
				
				StringBuilder url = new StringBuilder();
				/* Don't change the order of images within this if, it is index-based and UI will break.*/
				String[] imageFormats = {_large, _medium, _small};
				url.append(getSASURL(mapOfIdAndFilePath.get(id), util.generateSASToken(azureBlobClient, mapOfIdAndFilePath.get(id))));
				String replaceString = mapOfIdAndFilePath.get(id).substring(mapOfIdAndFilePath.get(id).lastIndexOf('.'),
						mapOfIdAndFilePath.get(id).length());
				for(String format: Arrays.asList(imageFormats)) {
					url.append(",");
					String path = mapOfIdAndFilePath.get(id);
					path = path.replaceAll(replaceString, format + replaceString);
					url.append(getSASURL(path, util.generateSASToken(azureBlobClient, path)));
				}
				mapOfIdAndSASUrls.put(id, url.toString());
			}else {
				mapOfIdAndSASUrls.put(id, getSASURL(mapOfIdAndFilePath.get(id), util.generateSASToken(azureBlobClient, mapOfIdAndFilePath.get(id))));
			}
		});
		return mapOfIdAndSASUrls;
	}
	
	public Resource read(FileLocation fileLocation) {
		Resource resource = null;
		long startTime = new Date().getTime();
		if(null == azureBlobClient)
			azureBlobClient = azureFacade.getAzureClient();
		
		CloudBlobContainer container= null;
		File localFile = null;
		CloudBlockBlob blob = null;
		String completeName = fileLocation.getFileName();
		int index = completeName.indexOf('/');
		String containerName = completeName.substring(0, index);
		String fileNameWithPath = completeName.substring(index + 1, completeName.length());
		try {
			if(isContainerFixed)
				container = azureBlobClient.getContainerReference(fixedContainerName);
			else
				container = azureBlobClient.getContainerReference(containerName);
			
			long beforeCalling = new Date().getTime();
			
			blob = container.getBlockBlobReference(fileNameWithPath);
			
			File dirPath = new File(TEMP_FILE_PATH_NAME);
			if(dirPath.exists()||dirPath.mkdirs()) {
				String fileName = fileNameWithPath.substring(fileNameWithPath.lastIndexOf('/')+1,fileNameWithPath.length());
				localFile = new File(TEMP_FILE_PATH_NAME+fileName);
				if(!(localFile.exists() || localFile.createNewFile())) {
					throw new RuntimeException("Unable to create temp file");
				}
			}
			else {
				throw new RuntimeException("Unable to create temp directory");
			}
			
			blob.downloadToFile(localFile.getPath());

			long afterAws = new Date().getTime();

			resource = new FileSystemResource(localFile);

			long generateResource = new Date().getTime();

			log.info(" the time to prep Obj : " + (beforeCalling - startTime));
			log.info(" the time to get object from aws " + (afterAws - beforeCalling));
			log.info(" the time for creating resource form file : " + (generateResource - afterAws));
		}catch(Exception e) {
			log.error("Exceptione while downloading file: ", e);
		}
		finally {
			try {
				Files.deleteIfExists(localFile.toPath());
			} catch (IOException e) {
				log.error("Exceptione while deleting file: ", e);
			}
		}
        
        return resource;
	}
	
	
	/**
	 * Prepares the SASUrls for the resource on azure
	 * 
	 * @param path
	 * @param sasToken
	 * @return
	 */
	private String getSASURL(String path, String sasToken) {
		StringBuilder sasURL = new StringBuilder();
		String host = azureBlobStorageHost.replace("$accountName", azureAccountName);		
		sasURL.append(host).append("/").append(path).append("?").append(sasToken);
		return sasURL.toString();
	}

	
	/**
	 * Uploads the file to Azure Blob Storage
	 * 
	 * @param container
	 * @param completePath
	 * @param file
	 * @param image
	 * @param extension
	 */
	public void upload(CloudBlobContainer container, String completePath, MultipartFile file, BufferedImage image, String extension) {
		try{
			if(null == file && null != image) {
				ByteArrayOutputStream os = new ByteArrayOutputStream();
				ImageIO.write(image, extension, os);
				CloudBlockBlob blob = container.getBlockBlobReference(completePath);
				blob.upload(new ByteArrayInputStream(os.toByteArray()), 8*1024*1024);
			}else {
				CloudBlockBlob blob = container.getBlockBlobReference(completePath);
				blob.upload(file.getInputStream(), file.getSize());
			}

		}catch(Exception e) {
			e.printStackTrace();
			log.error("Exception while uploading the file: ",e);
		}
						  
	}
	
	
}
