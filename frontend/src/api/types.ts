export interface UserMe {
  userId: number
  username: string
  roleName: string
  permissions: string[]
}

export interface DashboardAlerts {
  expiringLicenses: number
  overdueInvoices: number
  maintenanceDue: number
  pendingAssignments: number
}

export interface TokenResponse {
  access_token: string
  refresh_token?: string
  token_type: string
  expires_in: number
}

export interface Client {
  id: number
  name: string
  rfc: string
  address: string
  phone: string
  email: string
  contactName: string
  clientType: string
  paymentTerms: string
  creditLimit: number
  creditDays: number
  status: string
}

export interface ClientRate {
  id: number
  clientId: number
  routeId: number
  routeLabel: string
  rate: number
  validFrom: string
  validTo: string | null
}

export interface Route {
  id: number
  origin: string
  destination: string
  estimatedKm: number
  description: string
}

export interface License {
  id: number
  licenseNumber: string
  licenseType: string
  issueDate: string | null
  expirationDate: string | null
}

export interface Employee {
  id: number
  name: string
  address: string
  phone: string
  email: string
  rfc: string
  curp: string
  emergencyContactName: string
  emergencyContactPhone: string
  license: License | null
  status: string
}

export interface Vehicle {
  id: number
  internalCode: string
  plates: string
  brand: string
  model: string
  year: number | null
  serialNumber: string
  vehicleType: string
  loadCapacity: number
  mileage: number
  status: string
}

export interface Maintenance {
  id: number
  vehicleId: number
  vehicleLabel: string
  maintenanceDate: string
  odometerReading: number
  type: string
  workPerformed: string
  provider: string
  cost: number
  nextServiceDate: string | null
  nextServiceKm: number | null
}

export interface FuelLoad {
  id: number
  vehicleId: number
  vehicleLabel: string
  tripId: number | null
  tripLabel: string | null
  fuelStation: string
  loadDate: string
  liters: number
  pricePerLiter: number
  amount: number
  odometerReading: number
}

export interface ServiceRequest {
  id: number
  folio: string
  clientId: number
  clientName: string
  routeId: number
  routeLabel: string
  cargoDescription: string
  estimatedWeight: number | null
  packageCount: number
  packageWeight: number | null
  pickupScheduled: string | null
  deliveryScheduled: string | null
  agreedRate: number | null
  requiresDocuments: boolean
  status: string
  notes: string | null
  createdAt: string | null
}

export interface CargoPackage {
  id: number
  serviceRequestId: number
  lineNo: number
  description: string
  quantity: number | null
  unit: string
  unitWeight: number | null
  receivedQuantity: number | null
  receiptCondition: string | null
}

export interface Trip {
  id: number
  serviceRequestId: number
  folio: string
  clientName: string
  routeLabel: string
  vehicleId: number
  vehicleLabel: string
  employeeId: number
  employeeName: string
  estimatedKm: number | null
  actualKm: number | null
  plannedStart: string | null
  plannedEnd: string | null
  departure: string | null
  arrival: string | null
  status: string
}

export interface AssignmentOptions {
  vehicles: Vehicle[]
  operators: Employee[]
}
