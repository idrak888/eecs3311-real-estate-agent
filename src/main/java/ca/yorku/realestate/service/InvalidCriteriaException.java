package ca.yorku.realestate.service;

import java.util.List;

/** The criteria cannot be searched, e.g. an inverted price range. The search did not run. */
public class InvalidCriteriaException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final transient List<String> errors;

    public InvalidCriteriaException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
