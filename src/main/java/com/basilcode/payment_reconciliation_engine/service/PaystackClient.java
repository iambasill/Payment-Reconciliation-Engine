package com.basilcode.payment_reconciliation_engine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaystackClient {

    private final RestClient paystackRestClient;


    public JsonNode listTransactions(int perPage, int page) {
        return paystackRestClient.get()
                .uri("/transaction?perPage={perPage}&page={page}", perPage, page)
                .retrieve()
                .body(JsonNode.class);
    }


    public JsonNode verifyTransaction(String reference) {
        return paystackRestClient.get()
                .uri("/transaction/verify/{reference}", reference)
                .retrieve()
                .body(JsonNode.class);
    }

    public List<JsonNode> getAllTransactionsForDate(String date) {
        List<JsonNode> allTransactions = new ArrayList<>();
        int currentPage = 1;
        int perPage = 100;
        boolean hasMorePages = true;

        while (hasMorePages) {
            JsonNode response = paystackRestClient.get()
                    .uri("/transaction?from={date}&to={date}&perPage={perPage}&page={page}",
                            date, date, perPage, currentPage)
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode data = response.get("data");
            if (data != null && data.isArray()) {
                for (JsonNode transaction : data) {
                    allTransactions.add(transaction);
                }
            }

            JsonNode meta = response.get("meta");
            if (meta == null || currentPage >= meta.get("total_pages").asInt()) {
                hasMorePages = false;
            } else {
                currentPage++;
            }
        }

        return allTransactions;
    }
}