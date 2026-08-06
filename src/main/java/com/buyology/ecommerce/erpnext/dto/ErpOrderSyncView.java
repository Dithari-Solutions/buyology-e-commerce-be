package com.buyology.ecommerce.erpnext.dto;

import java.math.BigDecimal;

/**
 * Per-order ERPNext sync state for the admin ERP page.
 *
 * @param orderId          Buyology order id
 * @param status           order status (only PAID and later are ever pushed)
 * @param totalAmount      order total
 * @param currency         order currency
 * @param paidAt           when the order was paid (ISO-8601), null if unpaid
 * @param erpSalesOrder    ERPNext Sales Order name, null until synced
 * @param erpSalesInvoice  ERPNext Sales Invoice name, null until synced
 * @param erpSyncedAt      when the push last succeeded (ISO-8601)
 * @param erpSyncError     last failure message, null when healthy
 * @param salesOrderUrl    deep link into the ERPNext desk, null until synced
 * @param salesInvoiceUrl  deep link into the ERPNext desk, null until synced
 */
public record ErpOrderSyncView(
        String orderId,
        String status,
        BigDecimal totalAmount,
        String currency,
        String paidAt,
        String erpSalesOrder,
        String erpSalesInvoice,
        String erpSyncedAt,
        String erpSyncError,
        String salesOrderUrl,
        String salesInvoiceUrl
) {}
