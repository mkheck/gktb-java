package com.thehecklers.gktbjava;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@SpringBootApplication
public class GktbJavaApplication {

    public static void main(String[] args) {
        SpringApplication.run(GktbJavaApplication.class, args);
    }

    @Bean
    WebClient client() {
        return WebClient.create("http://localhost:9876/metar");
    }

    @Bean
    RouterFunction<ServerResponse> routerFunction(AirportInfoService svc) {
        return route(GET("/"), svc::allAirports)
                .andRoute(GET("/{id}"), svc::airportById)
                .andRoute(GET("/metar/{id}"), svc::metar);
    }

    @Bean
    CommandLineRunner loadData(AirportRepository repo) {
        return args -> {
            repo.deleteAll()
                    .thenMany(
                            Flux.just(new Airport("KGAG", "Gage Airport"),
                                    new Airport("KLOL", "Derby Field"),
                                    new Airport("KBUM", "Butler Memorial Airport"),
                                    new Airport("KSTL", "St. Louis Lambert International Airport"),
                                    new Airport("KORD", "O'Hare International Airport"))
                    ).flatMap(repo::save)
                    .log()
                    .subscribe();
        };
    }
}

@Service
class AirportInfoService {
    private final AirportRepository repo;
    private final WebClient wxClient;

    public AirportInfoService(AirportRepository repo, WebClient wxClient) {
        this.repo = repo;
        this.wxClient = wxClient;
    }

    public Mono<ServerResponse> allAirports(ServerRequest req) {
        return ok().body(repo.findAll(), Airport.class);
    }

    public Mono<ServerResponse> airportById(ServerRequest req) {
        return ok().body(repo.findById(req.pathVariable("id")), Airport.class);
    }

    public Mono<ServerResponse> metar(ServerRequest req) {
        return ok().body(wxClient.get()
                .uri("?loc=" + req.pathVariable("id"))
                .retrieve()
                .bodyToMono(METAR.class), METAR.class);
    }
}

interface AirportRepository extends ReactiveCrudRepository<Airport, String> {
}

@Document
record Airport(@Id String id, String name) {}

record METAR(String flight_rules, String raw) {}
