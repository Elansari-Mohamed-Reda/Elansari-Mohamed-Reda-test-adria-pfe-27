package ma.adria.transaction.web.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.ConstraintViolationException;
import ma.adria.transaction.service.exception.TransactionNotFoundException;
import ma.adria.transaction.service.exception.TransactionRejectedException;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

@Component
public class GraphqlExceptionHandler extends DataFetcherExceptionResolverAdapter {
    @Override
    protected GraphQLError resolveToSingleError(Throwable exception, DataFetchingEnvironment environment) {
        if (exception instanceof TransactionNotFoundException) {
            return GraphqlErrorBuilder.newError(environment).errorType(ErrorType.NOT_FOUND)
                    .message(exception.getMessage()).build();
        }
        if (exception instanceof TransactionRejectedException || exception instanceof ConstraintViolationException
                || exception instanceof ma.adria.transaction.service.exception.TransactionConflictException || exception instanceof IllegalArgumentException) {
            return GraphqlErrorBuilder.newError(environment).errorType(ErrorType.BAD_REQUEST)
                    .message("Donnees de transaction invalides.").build();
        }
        return null;
    }
}
