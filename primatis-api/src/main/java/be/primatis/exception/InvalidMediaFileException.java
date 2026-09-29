package be.primatis.exception;

/**
 * Indique qu'un fichier média fourni ne respecte pas les contraintes d'upload.
 */
public class InvalidMediaFileException extends ApiException {

    private static final String DEFAULT_CODE = "ARTICLE_MEDIA_INVALID_FILE";

    public InvalidMediaFileException(String message) {
        super(DEFAULT_CODE, message);
    }

    public InvalidMediaFileException(String code, String message) {
        super(code, message);
    }
}
