package be.primatis.security;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;


@Service
@Profile("test")
public class RbacMethodSecuritySampleService {

    @PreAuthorize("hasAuthority('LOAN_MANAGE')")
    public String manageLoan() {
        return "loan-managed";
    }

    @PreAuthorize("hasAuthority('CATALOGUE_READ')")
    public String readCatalogue() {
        return "catalogue-read";
    }

    @PreAuthorize("hasAuthority('CATALOGUE_MANAGE')")
    public String manageCatalogue() {
        return "catalogue-managed";
    }

    @PreAuthorize("hasAuthority('ARTICLE_MANAGE')")
    public String manageArticle() {
        return "article-managed";
    }

    @PreAuthorize("hasAuthority('ARTICLE_PUBLISH')")
    public String publishArticle() {
        return "article-published";
    }

    
    public String readOwnLoans(Long ownerId, Authentication authentication) {
        String authenticatedUserId = authentication.getName();
        if (!String.valueOf(ownerId).equals(authenticatedUserId)) {
            throw new AccessDeniedException("L'utilisateur authentifié n'est pas propriétaire de cette ressource.");
        }
        return "own-loans-of-" + ownerId;
    }
}
