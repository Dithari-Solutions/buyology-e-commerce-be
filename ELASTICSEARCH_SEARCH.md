# Elasticsearch Product Search Integration

This project now uses Elasticsearch to provide high-performance, full-text search capabilities for products.

## Overview

The integration consists of:
- **Elasticsearch Document:** `ProductDocument` maps the SQL `Product` and `ProductTranslation` entities into a searchable format.
- **Repository:** `ProductSearchRepository` extends `ElasticsearchRepository` for standard CRUD operations on the index.
- **Service:** `ProductSearchService` handles the mapping from JPA entities to Elasticsearch documents and executes complex search queries.
- **Auto-Indexing:** `ProductService` automatically indexes new products into Elasticsearch upon creation.

## Configuration

Elasticsearch is configured in `src/main/resources/application.properties`:

```properties
# Elasticsearch Configuration
spring.elasticsearch.uris=http://localhost:9200
# spring.elasticsearch.username=
# spring.elasticsearch.password=
```

## Search API

A new public endpoint has been added for Elasticsearch-powered search:

### `GET /api/product/search-elastic`

Performs a full-text search across product titles, descriptions, categories, and brands.

**Parameters:**
- `query` (required): The search string (e.g., "iPhone", "gaming laptop").
- `lang` (required): Language code (e.g., "EN", "AZ", "AR").
- `countryCode` (optional): ISO 3166-1 alpha-3 code to scope results to a specific country's stores.
- `currency` (optional): ISO 4217 currency code for price display.
- `lat` / `lng` (optional): Coordinates for calculating express delivery availability.

**Example Request:**
```bash
GET /api/product/search-elastic?query=macbook&lang=EN&countryCode=UAE
```

## Indexed Fields

The following fields are indexed and searchable:
- `translations.title` (analyzed text)
- `translations.description` (analyzed text)
- `categoryName` (analyzed text)
- `brandName` (analyzed text)
- `sku` (keyword)
- `status` (keyword)
- `productType` (keyword)
- `availabilityStatus` (keyword)

## Technical Details

### Multi-Match Query
The search service uses a `multiMatch` query to find relevant products across multiple fields. This ensures that a search for a brand or category name also returns matching products.

### Data Syncing
Currently, indexing is performed during product creation in `ProductService.createProduct`. For existing data, a one-time migration or a re-indexing task may be required.

### Order Maintenance
The search results maintain the relevance score order returned by Elasticsearch while still applying business logic like country-specific pricing and availability filters in the application layer.
