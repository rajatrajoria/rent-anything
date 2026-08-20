package com.rajat.rent_anything.kyc.application;

import org.springframework.web.multipart.MultipartFile;

/**
 * Storage abstraction for KYC documents (ID photo, selfie).
 * <p>
 * Deliberately separate from {@code ImageStorageService} (used for item
 * photos): that interface's key format and public-URL intent are specific
 * to item images, and KYC documents must never be reachable from a public
 * route — URLs are only ever generated for the submission's own user or
 * an admin.
 */
public interface KycDocumentStorageService {

    /**
     * Uploads a KYC document and returns the generated storage key.
     * <p>
     * Example returned key: kyc/42/idDocument/8d7a5f6c.jpg
     *
     * @param userId owner of the submission
     * @param kind   document kind, e.g. "idDocument" or "selfie"
     * @param file   document file
     * @return storage key
     */
    String upload(Long userId, String kind, MultipartFile file);

    /**
     * Deletes a document from storage. Used when a resubmission replaces
     * previously uploaded documents.
     *
     * @param documentKey storage key
     */
    void delete(String documentKey);

    /**
     * Generates a short-lived, non-public URL for a document. Callers must
     * only invoke this for the submission's own user or an admin.
     *
     * @param documentKey storage key
     * @return presigned URL
     */
    String getDocumentUrl(String documentKey);
}
