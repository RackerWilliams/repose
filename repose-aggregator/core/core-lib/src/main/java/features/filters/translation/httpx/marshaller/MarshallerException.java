package features.filters.translation.httpx.marshaller;

/**
 * @author fran
 */
public class MarshallerException extends RuntimeException {
    public MarshallerException(String message, Throwable throwable) {
        super(message, throwable);        
    }
}
