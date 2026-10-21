export type Product = {
  specifications?: Record<string, string>;
  averageRating?: number;
  reviewCount?: number;
  unitCost?: number | null;
  receivedQuantity?: number;
  totalCost?: number | null;
  supplierName?: string | null;
  receivedAt?: string;
  id: number;
  categoryId?: number;
  variantId?: number;
  name: string;
  sku: string;
  category: string;
  price: number;
  stock: number;
  threshold: number;
  color: string;
  image: string;
};

export type Order = {
  apiStatus?: string;
  paymentMethod?: string;
  id: string;
  apiId?: number;
  orderRef?: string;
  deliveryStatus?: "NONE" | "PREPARING" | "SHIPPED" | "DELIVERED";
  customer: string;
  date: string;
  total: number;
  status: "Chờ xác nhận" | "Đang xử lý" | "Đang giao" | "Hoàn tất" | "Đã hủy" | "Thanh toán lỗi";
  payment: "Đã thanh toán" | "Chờ thanh toán" | "Đã hủy" | "Hoàn tiền mô phỏng" | "Thanh toán lỗi";
  product: string;
  quantity: number;
};

export type ReturnRequest = {
  id: string;
  apiId?: number;
  orderId?: number;
  restockable?: boolean | null;
  order: string;
  customer: string;
  reason: string;
  status: "Chờ xử lý" | "Đã chấp nhận" | "Đã nhập kho" | "Đã từ chối";
};

export const retiredCatalogSkus: string[] = [
  "MOC-BINH-001",
  "MOC-BINH-002",
  "MOC-BINH-003",
  "MOC-BINH-004",
  "MOC-TUI-001",
  "MOC-TUI-002",
  "MOC-TUI-003",
  "MOC-TUI-004",
  "MOC-SO-001",
  "MOC-SO-002",
  "MOC-SO-003",
  "MOC-SO-004",
  "MOC-DEN-001",
  "MOC-DEN-002",
  "MOC-DEN-003",
  "MOC-DEN-004",
  "MOC-COC-001",
  "MOC-COC-002",
  "MOC-COC-003",
  "MOC-COC-004",
  "MOC-NEN-001",
  "MOC-NEN-002",
  "MOC-NEN-003",
  "MOC-NEN-004",
  "MST001"
];

export const seedProducts: Product[] = [
  {
    "id": 1,
    "name": "Guitar acoustic Mộc A100",
    "sku": "MOC-ACOU-001",
    "category": "Guitar acoustic",
    "price": 2790000,
    "stock": 12,
    "threshold": 3,
    "color": "sage",
    "image": "https://images.unsplash.com/photo-1510915361894-db8b60106cb1?auto=format&fit=crop&w=1000&q=80"
  },
  {
    "id": 2,
    "name": "Guitar điện Mộc E100",
    "sku": "MOC-ELEC-001",
    "category": "Guitar điện",
    "price": 4190000,
    "stock": 10,
    "threshold": 3,
    "color": "coral",
    "image": "https://images.unsplash.com/photo-1564186763535-ebb21ef5277f?auto=format&fit=crop&w=1000&q=80"
  },
  {
    "id": 3,
    "name": "Piano cơ Mộc P100",
    "sku": "MOC-PIANO-001",
    "category": "Đàn piano",
    "price": 24900000,
    "stock": 4,
    "threshold": 2,
    "color": "yellow",
    "image": "https://images.unsplash.com/photo-1520523839897-bd0b52f945a0?auto=format&fit=crop&w=1000&q=80"
  },
  {
    "id": 4,
    "name": "Bộ trống acoustic Mộc D100",
    "sku": "MOC-DRUM-001",
    "category": "Trống acoustic",
    "price": 9290000,
    "stock": 7,
    "threshold": 2,
    "color": "blue",
    "image": "https://images.unsplash.com/photo-1519892300165-cb5542fb47c7?auto=format&fit=crop&w=1000&q=80"
  },
  {
    "id": 5,
    "name": "Violin Mộc V100 cỡ 1/2",
    "sku": "MOC-VIOL-001",
    "category": "Đàn violin",
    "price": 1390000,
    "stock": 14,
    "threshold": 4,
    "color": "pink",
    "image": "https://images.unsplash.com/photo-1612225330812-01a9c6b355ec?auto=format&fit=crop&w=1000&q=80"
  },
  {
    "id": 6,
    "name": "Trumpet Mộc T100",
    "sku": "MOC-TRUMP-001",
    "category": "Kèn trumpet",
    "price": 3190000,
    "stock": 9,
    "threshold": 3,
    "color": "wood",
    "image": "https://images.unsplash.com/photo-1511192336575-5a79af67a629?auto=format&fit=crop&w=1000&q=80"
  }
];

export const seedOrders: Order[] = [
  {
    "id": "SF-2408",
    "customer": "Nguyễn Minh Anh",
    "date": "Hôm nay",
    "total": 5580000,
    "status": "Đang giao",
    "payment": "Đã thanh toán",
    "product": "Guitar acoustic Mộc A100",
    "quantity": 2
  },
  {
    "id": "SF-2407",
    "customer": "Trần Hoàng Nam",
    "date": "Hôm nay",
    "total": 4190000,
    "status": "Chờ xác nhận",
    "payment": "Chờ thanh toán",
    "product": "Guitar điện Mộc E100",
    "quantity": 1
  },
  {
    "id": "SF-2406",
    "customer": "Lê Thu Hà",
    "date": "Hôm qua",
    "total": 24900000,
    "status": "Hoàn tất",
    "payment": "Đã thanh toán",
    "product": "Piano cơ Mộc P100",
    "quantity": 1
  },
  {
    "id": "SF-2405",
    "customer": "Phạm Gia Bảo",
    "date": "Hôm qua",
    "total": 9290000,
    "status": "Đang xử lý",
    "payment": "Đã thanh toán",
    "product": "Bộ trống acoustic Mộc D100",
    "quantity": 1
  },
  {
    "id": "SF-2404",
    "customer": "Vũ Khánh Linh",
    "date": "Hôm qua",
    "total": 1390000,
    "status": "Hoàn tất",
    "payment": "Đã thanh toán",
    "product": "Violin Mộc V100 cỡ 1/2",
    "quantity": 1
  }
];

export const seedReturns: ReturnRequest[] = [
  {
    "id": "RT-031",
    "order": "SF-2406",
    "customer": "Lê Thu Hà",
    "reason": "Kiểm tra lại bộ máy và cảm giác phím theo yêu cầu khách",
    "status": "Chờ xử lý"
  },
  {
    "id": "RT-030",
    "order": "SF-2404",
    "customer": "Vũ Khánh Linh",
    "reason": "Đổi cỡ đàn violin cho vừa tay người chơi",
    "status": "Đã chấp nhận"
  }
];

export const money = (value: number) => new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND", maximumFractionDigits: 0 }).format(value);
