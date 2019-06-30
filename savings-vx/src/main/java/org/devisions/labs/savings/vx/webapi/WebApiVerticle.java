package org.devisions.labs.savings.vx.webapi;

import io.netty.handler.codec.http.HttpStatusClass;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.devisions.labs.savings.vx.config.MainConfig;
import org.devisions.labs.savings.vx.services.SavingsAccountsService;
import org.devisions.labs.savings.vx.services.SavingsAccountsServiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class WebApiVerticle extends AbstractVerticle implements BaseWebApi {

    private SavingsAccountsService savingsAccountsService;

    private static final JsonObject config = MainConfig.getInstance().getConfig();

    private static final Logger logger = LoggerFactory.getLogger(WebApiVerticle.class);

    @Override
    public void start(Future<Void> startFuture) throws Exception {

        String savingsAccountsServiceAddress = config.getJsonObject("eventbus")
            .getString(SavingsAccountsServiceConfig.EB_ADDRESS);
        this.savingsAccountsService = SavingsAccountsService.createProxy(vertx, savingsAccountsServiceAddress);

        Future<Void> startupSteps = startupCommCheck()
            .compose(v -> apiSetup());

        startupSteps.setHandler(startFuture.completer());

    }

    /** Communication checks, performed at startup (verticle deployment) time. */
    private Future<Void> startupCommCheck() {

        Future<Void> responseFuture = Future.future();
        savingsAccountsService.commCheck(event -> {
            if (event.succeeded()) {
                logger.debug("Startup comm check ok.");
                responseFuture.complete();
            } else {
                logger.debug("Startup comm check failed! Error: {}", event.cause().getMessage());
                responseFuture.fail(event.cause());
            }
        });

        return responseFuture;

    }

    /** Web API setup (routes & http server). */
    private Future<Void> apiSetup() {

        Future<Void> responseFuture = Future.future();

        Router router = Router.router(vertx);

        // ------- API ROUTES -------
        router.get("/savings/:ownerId").handler(this::getSavingsAccountByOwnerHandler);

        int httpPort = config.getJsonObject("webApi").getInteger("httpPort");
        vertx.createHttpServer()
            .requestHandler(router::accept)
            .listen(httpPort, ar -> {
                if (ar.succeeded()) {
                    logger.info("WebApi server is running on port {}.", httpPort);
                    responseFuture.complete();
                } else {
                    logger.error("Failed to start WebApi server. Reason: {}", ar.cause().getMessage());
                    responseFuture.fail(ar.cause());
                }
            });

        return responseFuture;

    }

    /** The handler of "getSavingsAccountByOwner" requests. */
    private void getSavingsAccountByOwnerHandler(RoutingContext context) {

        HttpServerRequest request = context.request();
        String ownerId = request.getParam("ownerId");
        logger.debug("getSavingsAccountByOwnerHandler > Starting processing using ownerId {} ...", ownerId);
        savingsAccountsService.getSavingsAccountByOwner(ownerId, reply -> {
            HttpServerResponse response = context.response();
            response.putHeader("content-type", "application/json");
            JsonObject accountJson = reply.result();
            if (accountJson != null) {
                response.end(accountJson.encodePrettily());
            } else {
                response.setStatusCode(404);
                response.end();
            }
        });

    }



}