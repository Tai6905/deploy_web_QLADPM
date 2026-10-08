export type Product = {
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
  id: string;
  apiId?: number;
  orderRef?: string;
  deliveryStatus?: "NONE" | "PREPARING" | "SHIPPED" | "DELIVERED";
  customer: string;
  date: string;
  total: number;
  status: "Chờ xác nhận" | "Đang xử lý" | "Đang giao" | "Hoàn tất" | "Đã hủy";
  payment: "Đã thanh toán" | "Chờ thanh toán";
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

export const seedProducts: Product[] = [
  {
    id: 1,
    name: "Bình giữ nhiệt Flow 500ml",
    sku: "FLW-500-OL",
    category: "Đồ dùng",
    price: 285000,
    stock: 42,
    threshold: 12,
    color: "sage",
    image:
      "https://images.unsplash.com/photo-1602143407151-7111542de6e8?auto=format&fit=crop&w=120&q=80",
  },
  {
    id: 2,
    name: "Túi tote Canvas Daily",
    sku: "BAG-CT-01",
    category: "Phụ kiện",
    price: 165000,
    stock: 8,
    threshold: 10,
    color: "coral",
    image:
      "https://images.unsplash.com/photo-1544816155-12df9643f363?auto=format&fit=crop&w=120&q=80",
  },
  {
    id: 3,
    name: "Sổ tay giấy tái chế A5",
    sku: "NB-RC-A5",
    category: "Văn phòng phẩm",
    price: 89000,
    stock: 76,
    threshold: 15,
    color: "yellow",
    image:
      "https://images.unsplash.com/photo-1531346878377-a5be20888e57?auto=format&fit=crop&w=120&q=80",
  },
  {
    id: 4,
    name: "Đèn bàn Lumi Mini",
    sku: "LUM-MINI-WH",
    category: "Đồ dùng",
    price: 420000,
    stock: 5,
    threshold: 8,
    color: "blue",
    image:
      "https://images.unsplash.com/photo-1507473885765-e6ed057f782c?auto=format&fit=crop&w=120&q=80",
  },
  {
    id: 5,
    name: "Bình hoa gốm Mộc",
    sku: "CER-MOC-02",
    category: "Trang trí",
    price: 350000,
    stock: 19,
    threshold: 6,
    color: "pink",
    image:
      "https://images.unsplash.com/photo-1578500494198-246f612d3b3d?auto=format&fit=crop&w=120&q=80",
  },
  {
    id: 6,
    name: "Khay gỗ đa năng Nami",
    sku: "WOOD-NAMI-01",
    category: "Trang trí",
    price: 230000,
    stock: 3,
    threshold: 8,
    color: "wood",
    image:
      "https://images.unsplash.com/photo-1603199506016-b9a594b593c0?auto=format&fit=crop&w=120&q=80",
  },
];

export const seedOrders: Order[] = [
  {
    id: "SF-2408",
    customer: "Nguyễn Minh Anh",
    date: "Hôm nay, 10:42",
    total: 570000,
    status: "Đang giao",
    payment: "Đã thanh toán",
    product: "Bình giữ nhiệt Flow 500ml",
    quantity: 2,
  },
  {
    id: "SF-2407",
    customer: "Trần Hoàng Nam",
    date: "Hôm nay, 09:18",
    total: 254000,
    status: "Chờ xác nhận",
    payment: "Chờ thanh toán",
    product: "Túi tote Canvas Daily",
    quantity: 1,
  },
  {
    id: "SF-2406",
    customer: "Lê Thu Hà",
    date: "Hôm qua, 16:35",
    total: 509000,
    status: "Hoàn tất",
    payment: "Đã thanh toán",
    product: "Sổ tay giấy tái chế A5",
    quantity: 1,
  },
  {
    id: "SF-2405",
    customer: "Phạm Gia Bảo",
    date: "Hôm qua, 14:02",
    total: 420000,
    status: "Đang xử lý",
    payment: "Đã thanh toán",
    product: "Đèn bàn Lumi Mini",
    quantity: 1,
  },
  {
    id: "SF-2404",
    customer: "Vũ Khánh Linh",
    date: "25 thg 8, 11:20",
    total: 635000,
    status: "Hoàn tất",
    payment: "Đã thanh toán",
    product: "Bình hoa gốm Mộc",
    quantity: 1,
  },
];

export const seedReturns: ReturnRequest[] = [
  {
    id: "RT-031",
    order: "SF-2398",
    customer: "Hoàng Ngọc Mai",
    reason: "Sản phẩm không đúng mô tả",
    status: "Chờ xử lý",
  },
  {
    id: "RT-030",
    order: "SF-2391",
    customer: "Đặng Hải Yến",
    reason: "Đổi sang màu khác",
    status: "Đã chấp nhận",
  },
];

export const money = (value: number) =>
  new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(value);
