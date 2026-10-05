/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_DATA_SOURCE?: 'demo' | 'api'
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module "*.vue" {
  const component: import("vue").DefineComponent
  export default component
}