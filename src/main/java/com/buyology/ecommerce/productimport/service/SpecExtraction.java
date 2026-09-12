package com.buyology.ecommerce.productimport.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * The shape Claude is constrained to return for one spreadsheet row.
 *
 * <p>The input is a single unstructured string a person typed into Excel, and the same machine
 * appears in several shapes across one sheet — {@code "Lenovo T490 | Intel Core i7 | 8th Gen"},
 * {@code "Lenovo T490 Core i5 | 8th Gen | 16GB RAM | 256 SSD | 14\" INCH"},
 * {@code "CHROMEBOOK 4GB 32 GB"}, {@code "MONITOR  22 INCH"}. Pipes are inconsistent, order varies,
 * units are written five ways, and some rows are barely a description at all.
 *
 * <p>Every field is nullable on purpose. A row that genuinely does not say which generation the
 * processor is should come back with a null generation and a lower confidence, not a plausible
 * guess — a fabricated spec on a refurbished laptop listing is a customer dispute, and the whole
 * point of the review step is that a human sees what was uncertain.
 */
public record SpecExtraction(

        @JsonPropertyDescription("Manufacturer exactly as a customer would recognise it: Lenovo, HP, "
                + "Dell, Apple, Microsoft, Acer. Null if the row does not name one.")
        String brand,

        @JsonPropertyDescription("Model as the manufacturer writes it, without the brand and without "
                + "specs. 'T490', 'Latitude 7400', 'EliteBook 630 G11', 'MacBook Pro A2251'. Null if "
                + "the row names no model.")
        String model,

        @JsonPropertyDescription("What kind of device this is, chosen from exactly: LAPTOP, DESKTOP, "
                + "ALL_IN_ONE, TABLET, MONITOR, PHONE, ACCESSORY, OTHER. Use OTHER only when none fit.")
        String deviceType,

        @JsonPropertyDescription("Processor as written for a customer: 'Intel Core i7', "
                + "'Intel Pentium', 'Apple M1'. Exclude the generation. Null if absent.")
        String processor,

        @JsonPropertyDescription("Processor generation as a bare number, e.g. 10 for '10th Gen'. "
                + "Null if the row does not state one — do not infer it from the model.")
        Integer processorGeneration,

        @JsonPropertyDescription("RAM in gigabytes as a number. '16GB RAM' is 16. Null if absent.")
        Integer ramGb,

        @JsonPropertyDescription("Storage size in gigabytes as a number. '1 TB SSD' is 1024. "
                + "Null if absent.")
        Integer storageGb,

        @JsonPropertyDescription("Storage type: SSD, HDD, EMMC, or null if the row does not say.")
        String storageType,

        @JsonPropertyDescription("Screen size in inches as a number, e.g. 14 or 21.5. Null if absent.")
        Double screenInches,

        @JsonPropertyDescription("Operating system as a customer would read it: 'Windows 11 Pro', "
                + "'Windows 10 Pro', 'macOS', 'ChromeOS'. Null if the row does not state one.")
        String operatingSystem,

        @JsonPropertyDescription("Dedicated graphics memory in gigabytes, when the row mentions a GPU "
                + "such as '4GB GPU'. Null when the row says nothing about graphics.")
        Integer gpuGb,

        @JsonPropertyDescription("True only when the row explicitly says touch or touchscreen.")
        Boolean touchscreen,

        @JsonPropertyDescription("Colour if the row names one, e.g. 'Black'. Null otherwise.")
        String colour,

        @JsonPropertyDescription("A clean English product title a customer would see, built from the "
                + "fields above. Format: '<Brand> <Model> — <Processor> <Gen>, <RAM>GB RAM, "
                + "<Storage>GB <Type>'. Omit any part the row does not support. No pipes, no ALL CAPS, "
                + "no marketing words.")
        String titleEn,

        @JsonPropertyDescription("The same title in Azerbaijani. Model numbers, brand names and units "
                + "stay in Latin script exactly as in English; only the connecting words are "
                + "translated.")
        String titleAz,

        @JsonPropertyDescription("The same title in Arabic. Model numbers, brand names and units stay "
                + "in Latin script; only the connecting words are translated.")
        String titleAr,

        @JsonPropertyDescription("Two or three plain sentences describing the device for a shopper, in "
                + "English. State only what the row supports. Never invent battery life, condition "
                + "grade, warranty or included accessories.")
        String descriptionEn,

        @JsonPropertyDescription("The same description in natural Azerbaijani.")
        String descriptionAz,

        @JsonPropertyDescription("The same description in natural Arabic.")
        String descriptionAr,

        @JsonPropertyDescription("How confident you are that this row was understood correctly: HIGH "
                + "when the row is unambiguous and every important field was found, MEDIUM when it "
                + "parsed but something material is missing, LOW when the row is too vague to list "
                + "without a human reading it.")
        String confidence,

        @JsonPropertyDescription("Anything a human needs to decide, in short phrases — an ambiguous "
                + "abbreviation, a missing device type, a contradiction, a spec you could not place. "
                + "Empty when the row was clean.")
        List<String> needsReview
) {}
