package ma.adria.account.web.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.ConstraintViolationException;
import ma.adria.account.service.exception.AccountNotFoundException;
import ma.adria.account.service.exception.InvalidAccountException;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

@Component
public class GraphqlExceptionHandler extends DataFetcherExceptionResolverAdapter {
    @Override
    protected GraphQLError resolveToSingleError(Throwable exception, DataFetchingEnvironment environment) {
        if (exception instanceof AccountNotFoundException) {
            return GraphqlErrorBuilder.newError(environment).errorType(ErrorType.NOT_FOUND)
                    .message(exception.getMessage()).build();
        }
        if (exception instanceof InvalidAccountException || exception instanceof ConstraintViolationException
                || exception instanceof IllegalArgumentException) {
            return GraphqlErrorBuilder.newError(environment).errorType(ErrorType.BAD_REQUEST)
                    .message("Donnees du compte invalides.").build();
        }
        return null;
    }
}
