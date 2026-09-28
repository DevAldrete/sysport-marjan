export interface Option {
  value: string
  label: string
}

/** Domain vocabularies as they travel on the wire (database values) with Spanish labels. */

export const clientTypes: Option[] = [
  { value: 'occasional', label: 'Ocasional' },
  { value: 'frequent', label: 'Frecuente' },
]

export const paymentTerms: Option[] = [
  { value: 'cash', label: 'Contado' },
  { value: 'credit', label: 'Credito' },
]

export const clientStatuses: Option[] = [
  { value: 'active', label: 'Activo' },
  { value: 'inactive', label: 'Inactivo' },
]

/** Manual operator statuses (on_trip belongs to the trip lifecycle). */
export const employeeStatuses: Option[] = [
  { value: 'available', label: 'Disponible' },
  { value: 'resting', label: 'Descansando' },
  { value: 'vacation', label: 'Vacaciones' },
  { value: 'incapacitated', label: 'Incapacitado' },
  { value: 'terminated', label: 'Dado de baja' },
]

/** Manual vehicle statuses (assigned/on_trip belong to the trip lifecycle). */
export const vehicleStatuses: Option[] = [
  { value: 'available', label: 'Disponible' },
  { value: 'maintenance', label: 'Mantenimiento' },
  { value: 'out_of_service', label: 'Fuera de servicio' },
  { value: 'decommissioned', label: 'Dada de baja' },
]

export const maintenanceTypes: Option[] = [
  { value: 'preventive', label: 'Preventivo' },
  { value: 'corrective', label: 'Correctivo' },
]

export const packageUnits: Option[] = [
  { value: 'caja', label: 'Caja' },
  { value: 'paleta', label: 'Paleta' },
  { value: 'saco', label: 'Saco' },
  { value: 'bulto', label: 'Bulto' },
  { value: 'pieza', label: 'Pieza' },
  { value: 'contenedor', label: 'Contenedor' },
  { value: 'otro', label: 'Otro' },
]

export const requestStatuses: Option[] = [
  { value: 'requested', label: 'Solicitada' },
  { value: 'authorized', label: 'Autorizada' },
  { value: 'scheduled', label: 'Programada' },
  { value: 'assigned', label: 'Asignada' },
  { value: 'in_transit', label: 'En transito' },
  { value: 'delivered', label: 'Entregada' },
  { value: 'closed', label: 'Cerrada' },
  { value: 'cancelled', label: 'Cancelada' },
]

export const packageConditions: Option[] = [
  { value: 'ok', label: 'Completo' },
  { value: 'shortage', label: 'Faltante' },
  { value: 'damaged', label: 'Danado' },
  { value: 'missing', label: 'No llego' },
]

export const tripStatuses: Option[] = [
  { value: 'scheduled', label: 'Programado' },
  { value: 'in_transit', label: 'En transito' },
  { value: 'completed', label: 'Completado' },
  { value: 'cancelled', label: 'Cancelado' },
]

export function labelOf(options: Option[], value: string | null | undefined): string {
  return options.find((option) => option.value === value)?.label ?? (value ?? '')
}
