package com.buyology.ecommerce.productimport.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * A handful of spreadsheet rows extracted in one request.
 *
 * <p>Rows are sent in small batches rather than one at a time, and not only to save requests:
 * a sheet writes the same machine several ways, so a row that is ambiguous alone is often
 * unambiguous next to its neighbours — {@code "CHROMEBOOK 4GB 32 GB"} reads very differently in a
 * column of laptops than it would in isolation.
 *
 * <p>Each entry echoes its {@code rowNumber} back. The alternative — trusting that the returned
 * list lines up with the input by position — fails silently and in the worst possible way: every
 * product after a dropped row gets another row's specs, and nothing about the result looks wrong.
 */
public record SpecExtractionBatch(

        @JsonPropertyDescription("One entry for every input row, including rows you could not "
                + "understand. Never merge two input rows into one entry and never omit a row.")
        List<Item> rows
) {

    public record Item(

            @JsonPropertyDescription("The row number, copied exactly from the input line this came "
                    + "from. Do not renumber.")
            Integer rowNumber,

            @JsonPropertyDescription("What that row describes.")
            SpecExtraction product
    ) {}
}
