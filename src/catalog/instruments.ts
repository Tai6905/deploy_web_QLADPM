import schema from './instruments.json';

export type Specifications = Record<string, string>;
export type InstrumentField = { key: string; label: string; options: { value: string; label: string }[] };
export const instrumentTypes = schema.types;
export function instrumentDefinition(type?: string) {
  return instrumentTypes.find(item => item.value === type);
}
export function specificationLabel(key: string, value?: string) {
  if (!value) return 'Chưa cập nhật';
  if (key === 'instrumentType') return instrumentDefinition(value)?.label || 'Chưa cập nhật';
  const field = instrumentTypes.flatMap(type => type.fields).find(item => item.key === key);
  return field?.options.find(option => option.value === value)?.label || 'Chưa cập nhật';
}
export function setSpecification(specs: Specifications, key: string, value: string): Specifications {
  if (key === 'instrumentType') return value ? { instrumentType: value } : {};
  const next = { ...specs };
  if (value) next[key] = value; else delete next[key];
  return next;
}
export function matchesSpecifications(product: Specifications | undefined, filters: Specifications) {
  return Object.entries(filters).every(([key, value]) => product?.[key] === value);
}
export function comparisonSpecRows(products: { specifications?: Specifications }[]) {
  const types = instrumentTypes.filter(type => products.some(p => p.specifications?.instrumentType === type.value));
  return types.flatMap(type => type.fields.map(field => {
    const cells = products.map(product => product.specifications?.instrumentType !== type.value
      ? 'Không áp dụng' : specificationLabel(field.key, product.specifications?.[field.key]));
    return { key: field.key, label: field.label, cells, different: new Set(cells).size > 1 };
  }));
}
