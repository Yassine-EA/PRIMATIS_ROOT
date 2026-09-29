package be.primatis.article;

import be.primatis.config.ArticleMediaProperties;
import be.primatis.exception.InvalidMediaFileException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/**
 * Gère le stockage des images utilisées dans le contenu des articles.
 *
 * Les fichiers sont validés avant enregistrement et exposés par une URL
 * publique.
 */
@Service
public class ArticleMediaService {

    private static final Logger log = LoggerFactory.getLogger(ArticleMediaService.class);

    /**
     * Taille maximale autorisée pour une image, fixée à 5 Mio.
     */
    static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;

    /**
     * Associe chaque type d'image autorisé à son extension de fichier.
     */
    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp");

    /**
     * Signature binaire utilisée pour reconnaître une image JPEG.
     */
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    /**
     * Signature binaire utilisée pour reconnaître une image PNG.
     */
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    /**
     * Signature binaire du conteneur RIFF utilisé par le format WebP.
     */
    private static final byte[] RIFF_SIGNATURE = {'R', 'I', 'F', 'F'};
    /**
     * Signature binaire permettant d'identifier le format WebP.
     */
    private static final byte[] WEBP_SIGNATURE = {'W', 'E', 'B', 'P'};
    private static final int SIGNATURE_PROBE_LENGTH = 12;

    private static final String FILE_EMPTY_CODE = "ARTICLE_MEDIA_FILE_EMPTY";
    private static final String FILE_TOO_LARGE_CODE = "ARTICLE_MEDIA_FILE_TOO_LARGE";
    private static final String INVALID_FILE_TYPE_CODE = "ARTICLE_MEDIA_INVALID_FILE_TYPE";

    private final Path storageDirectory;

    public ArticleMediaService(ArticleMediaProperties properties) {
        this.storageDirectory = Path.of(properties.storagePath()).toAbsolutePath().normalize();
    }

    /**
     * Valide et stocke une image destinée à un article.
     *
     * @param file image à enregistrer
     * @return URL publique de l'image enregistrée
     */
    @PreAuthorize("hasAuthority('ARTICLE_MANAGE')")
    public String storeImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidMediaFileException(FILE_EMPTY_CODE, "Aucun fichier fourni.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidMediaFileException(
                    FILE_TOO_LARGE_CODE, "Le fichier dépasse la taille maximale autorisée (5 Mio).");
        }

        String contentType = file.getContentType();
        String extension = contentType != null ? EXTENSION_BY_CONTENT_TYPE.get(contentType) : null;
        if (extension == null) {
            throw new InvalidMediaFileException(INVALID_FILE_TYPE_CODE,
                    "Type de fichier non autorisé (JPEG, PNG ou WebP uniquement).");
        }

        if (!contentType.equals(detectImageContentType(file))) {
            throw new InvalidMediaFileException(INVALID_FILE_TYPE_CODE,
                    "Le contenu du fichier ne correspond pas au type d'image déclaré (JPEG, PNG ou WebP).");
        }

        String generatedFilename = UUID.randomUUID() + extension;
        Path target = storageDirectory.resolve(generatedFilename).normalize();
        if (!target.getParent().equals(storageDirectory)) {
            throw new InvalidMediaFileException(INVALID_FILE_TYPE_CODE, "Nom de fichier généré invalide.");
        }

        try {
            Files.createDirectories(storageDirectory);
            file.transferTo(target);
        } catch (IOException ex) {
            throw new UncheckedIOException("Écriture de l'image Article impossible.", ex);
        }

        log.info("Image Article stockée : {} ({} octets, {})", generatedFilename, file.getSize(), file.getContentType());
        return ArticleMediaProperties.PUBLIC_PATH_PREFIX + generatedFilename;
    }

    /**
     * Détermine le type d'image à partir de la signature du fichier.
     *
     * @param file fichier à analyser
     * @return type MIME détecté, ou null si le format n'est pas reconnu
     */
    private static String detectImageContentType(MultipartFile file) {
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(SIGNATURE_PROBE_LENGTH);
        } catch (IOException ex) {
            throw new UncheckedIOException("Lecture de l'image Article impossible.", ex);
        }

        if (startsWith(head, 0, JPEG_SIGNATURE)) {
            return "image/jpeg";
        }
        if (startsWith(head, 0, PNG_SIGNATURE)) {
            return "image/png";
        }
        if (startsWith(head, 0, RIFF_SIGNATURE) && startsWith(head, 8, WEBP_SIGNATURE)) {
            return "image/webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] data, int offset, byte[] signature) {
        return data.length >= offset + signature.length
                && Arrays.equals(data, offset, offset + signature.length, signature, 0, signature.length);
    }
}
