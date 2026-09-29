package be.primatis.config;

/**
 * Vérifie que les propriétés techniques ne contiennent aucun placeholder non résolu.
 */
final class UnresolvedPlaceholderGuard {

    private UnresolvedPlaceholderGuard() {
    }

    static String requireResolved(String propertyName, String value) {
        if (value != null && value.contains("${")) {
            throw new IllegalArgumentException("La propriété '" + propertyName
                    + "' référence une variable d'environnement non définie : " + value);
        }
        return value;
    }
}
