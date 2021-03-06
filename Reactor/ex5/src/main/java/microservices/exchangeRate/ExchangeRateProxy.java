package microservices.exchangeRate;

import datamodels.CurrencyConversion;
import io.reactivex.rxjava3.core.Single;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import utils.Options;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * This class serves as a proxy to the ExchangeRate microservice,
 * which returns the current exchange that converts one currency to
 * another.
 */
public class ExchangeRateProxy {
    /**
     * The URI that denotes a remote method to determine the current
     * exchange rate.
     */
    private final String mQueryExchangeRateURIAsync =
        "/microservices/exchangeRate/_exchangeRateAsync";

    /**
     * The WebClient provides the means to access the ExchangeRate
     * microservice.
     */
    private final WebClient mExchangeRate;

    /**
     * Host/port where the server resides.
     */
    private final String mSERVER_BASE_URL =
        "http://localhost:8081";

    /**
     * A cache of the latest exchange rate for a CurrencyConversion,
     * which is used if the ExchangeRate microservice fails to respond
     * before the designated timeout elapses.
     */
    private final List<CurrencyConversion> mExchangeRateCache =
        new ArrayList<>();

    /**
     * Constructor initializes the fields.
     */
    public ExchangeRateProxy() {
        mExchangeRate = WebClient
            // Start building.
            .builder()

            // The URL where the server is running.
            .baseUrl(mSERVER_BASE_URL)

            // Build the webclient.
            .build();
    }

    /**
     * Finds the exchange rate for the {@code sourceAndDestination}
     * asynchronously.
     *
     * @param scheduler The Scheduler context in which to run the
     *                  operation
     * @param currencyConversion The currency to convert from and to
     * @return A Mono containing the exchange rate.
     */
    public Mono<Double> queryExchangeRateForAsync(Scheduler scheduler,
                                                  CurrencyConversion currencyConversion) {
        // Return a mono to the exchange rate.
        return Mono
            .fromCallable(() -> mExchangeRate
                          // Create an HTTP GET request.
                          .get()

                          // Update the uri and add it to the baseUrl.
                          .uri(UriComponentsBuilder
                               .fromPath(mQueryExchangeRateURIAsync)
                               // Insert the currencyConversion into
                               // the URL.
                               .queryParam("currencyConversion", 
                                           currencyConversion)
                               .build()
                               .toString())

                          // Retrieve the response.
                          .retrieve()

                          // Convert it to a Mono of Double.
                          .bodyToMono(Double.class))
            
            // Schedule this to run on the given scheduler.
            .subscribeOn(scheduler)

            // De-nest the result so it's a Mono<Double>.
            .flatMap(Function.identity())

            // Update the cache with the latest rate.
            .flatMap(latestRate -> updateCachedRate(latestRate, currencyConversion))

            // If this computation runs for more than the configured
            // number of seconds return the last cached rate.
            .timeout(Options.instance().exchangeRateTimeout(),
                     getLastCachedRate(currencyConversion));
    }

    /**
     *
     * @param latestRate
     * @param currencyConversion
     * @return
     */
    private Mono<Double> updateCachedRate(Double latestRate,
                                          CurrencyConversion currencyConversion) {
        boolean updatedCacheEntry = false;
        for (CurrencyConversion cc : mExchangeRateCache)
            if (cc.getFrom().equals(currencyConversion.getFrom())
                    && cc.getTo().equals(currencyConversion.getTo())) {
                cc.setExchangeRate(latestRate);
                updatedCacheEntry = true;
            }

        if (!updatedCacheEntry) {
            currencyConversion.setExchangeRate(latestRate);
            mExchangeRateCache.add(currencyConversion);
        }

        return Mono.just(latestRate);
    }

    /**
     *
     * @param currencyConversion
     * @return
     */
    private Mono<Double> getLastCachedRate(CurrencyConversion currencyConversion) {
        for (CurrencyConversion cc : mExchangeRateCache)
            if (cc.getFrom().equals(currencyConversion.getFrom())
                && cc.getTo().equals(currencyConversion.getTo()))
                return Mono.just(cc.getExchangeRate());

        return Mono.just(Options.instance().defaultRate());
    }

    /**
     * Finds the exchange rate for the {@code sourceAndDestination} asynchronously.
     *
     * @param currencyConversion Indicates the currency to convert from and to
     * @return A Single containing the exchange rate.
     */
    public Single<Double> queryExchangeRateForAsyncRx(CurrencyConversion currencyConversion) {
        return Single
            // Return a Single to the exchange rate.
            .fromPublisher(Mono
                           .fromCallable(() -> mExchangeRate
                                         // Create an HTTP GET
                                         // request.
                                         .get()

                                         // Add the uri to the
                                         // baseUrl.
                                         .uri(UriComponentsBuilder
                                              .fromPath(mQueryExchangeRateURIAsync)
                                              .queryParam("currencyConversion",
                                                          currencyConversion)
                                              .build()
                                              .toString())

                                         // Retrieve the response.
                                         .retrieve()

                                         // Convert it to a Mono of
                                         // Double.
                                         .bodyToMono(Double.class))

                           // Schedule this to run on the given
                           // scheduler.
                           .subscribeOn(Schedulers.parallel())

                           // De-nest the result so it's a
                           // Mono<Double>.
                           .flatMap(Function.identity())

                           // If this computation runs for more than the configured number of
                           // seconds return the last cached rate.
                           .timeout(Options.instance().exchangeRateTimeout(),
                                    getLastCachedRate(currencyConversion)));
    }

    /**
     * Finds the exchange rate for the {@code sourceAndDestination} synchronously.
     *
     * @param currencyConversion The currency to convert from and to
     * @return A Mono containing the exchange rate.
     */
    public Mono<Double> queryExchangeRateForSync(CurrencyConversion currencyConversion) {
        // Return a mono to the exchange rate.
        return Mono
            .fromCallable(() -> mExchangeRate
                          // Create an HTTP GET request.
                          .get()

                          // Add the uri to the baseUrl.
                          .uri(UriComponentsBuilder
                               .fromPath(mQueryExchangeRateURIAsync)
                               .queryParam("currencyConversion", currencyConversion)
                               .build()
                               .toString())

                          // Retrieve the response.
                          .retrieve()

                          // Convert it to a Mono of Double.
                          .bodyToMono(Double.class))
            
            // De-nest the result so it's a Mono<Double>.
            .flatMap(Function.identity())

            // If this computation runs for more than the configured number of
            // seconds return the last cached rate.
            .timeout(Options.instance().exchangeRateTimeout(),
                     getLastCachedRate(currencyConversion));
    }
}
