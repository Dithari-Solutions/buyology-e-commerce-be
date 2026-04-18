# Admin Dashboard: Order & Delivery Proof Integration

This guide covers the integration of the enhanced order details API, which merges ecommerce data with courier-service evidence (photos/proofs).

## 1. API Endpoint

**Endpoint:** `GET /api/admin/orders/{orderId}/with-proof`  
**Base URL:** `/api/admin/orders`  
**Authentication:** Admin JWT (Bearer Token)

### Response Payload (`OrderAdminResponse`)
This response extends the standard `OrderResponse` with courier-specific evidence. All image URLs are absolute **presigned Contabo S3 URLs**.

```json
{
  "id": "...",
  "status": "DELIVERED",
  "storeId": "uuid",           // The fulfilling store
  "courierName": "John Smith",
  "courierPhone": "+971...",
  "pickupProofImageUrl": "https://eu2.contabostorage.com/...",
  "pickupProofTakenAt": "2026-04-19T10:00:00Z",
  "deliveryProofImageUrl": "https://eu2.contabostorage.com/...",
  "deliveryProofSignatureUrl": "...", 
  "deliveredTo": "Alice Johnson",
  "deliveryProofTakenAt": "2026-04-19T10:30:00Z",
  "trackingHistory": [ ... ]    // Full event log
}
```

---

## 2. Admin UI Strategy

### Order Header
Display the **Store ID** and **Status** prominently. For `EXPRESS` orders, show the courier's contact details.

### "Chain of Custody" Section
For orders that are `PICKED_UP` or `DELIVERED`, use the proof fields to build a visual evidence timeline.

#### A. Pickup Evidence
- **Trigger:** Only show if `pickupProofImageUrl` is not null.
- **Display:** Show the image with the `pickupProofTakenAt` timestamp.
- **Label:** "Package Collected from Store"

#### B. Delivery Evidence
- **Trigger:** Only show if `deliveryProofImageUrl` is not null.
- **Display:** Show the delivery photo and the signature (if available).
- **Recipient:** Display `deliveredTo` (e.g., "Received by: Alice").
- **Label:** "Drop-off Proof"

---

## 3. Implementation Example (React)

```typescript
const OrderDetail = ({ orderId }) => {
  const [order, setOrder] = useState<OrderAdminResponse | null>(null);

  useEffect(() => {
    fetch(`/api/admin/orders/${orderId}/with-proof`, {
      headers: { Authorization: `Bearer ${adminToken}` }
    })
    .then(res => res.json())
    .then(data => setOrder(data.data));
  }, [orderId]);

  if (!order) return <Spinner />;

  return (
    <div>
      <h1>Order #{order.id}</h1>
      <p>Store ID: {order.storeId}</p>

      {/* Proof Section */}
      <div className="proof-grid">
        {order.pickupProofImageUrl && (
          <div className="card">
            <h3>Pickup Proof</h3>
            <img src={order.pickupProofImageUrl} alt="Pickup" />
            <span>Time: {new Date(order.pickupProofTakenAt).toLocaleString()}</span>
          </div>
        )}

        {order.deliveryProofImageUrl && (
          <div className="card">
            <h3>Delivery Proof</h3>
            <img src={order.deliveryProofImageUrl} alt="Delivery" />
            <p>Delivered to: {order.deliveredTo}</p>
            <span>Time: {new Date(order.deliveryProofTakenAt).toLocaleString()}</span>
          </div>
        )}
      </div>
    </div>
  );
};
```

---

## 4. Troubleshooting

| Issue | Reason | Solution |
| :--- | :--- | :--- |
| **Proof fields are null** | Order is `REGULAR` or still `PAID`. | Only expect proof for `EXPRESS` orders that have reached `PICKED_UP`. |
| **Images not loading** | Local dev networking. | Ensure the courier service storage URL is accessible from your browser. |
| **403 Forbidden** | Insufficient permissions. | Ensure the user has the `ADMIN` role. |
