# RailReserve

RailReserve is a software engineering project developed to simulate a railway reservation and travel management system for Android users. The project combines Android application development, database management, and backend API integration to provide a realistic railway booking workflow in a controlled demo environment. It is designed as a practical, report-friendly project that demonstrates software architecture, OOP principles, integration, and testing in a real-world application context.

The system allows users to search trains, view schedules, check fare and availability information, and store demo booking records locally on the mobile device. It does not process real ticketing or payments and is intended for educational and demonstration purposes.

## 1. Project Overview

Railway reservation systems are widely used in daily life and involve several technical challenges such as route search, scheduling, ticket generation, data persistence, secure API access, and database management. RailReserve addresses these challenges by building a complete mobile application that provides a simplified but realistic reservation workflow.

The project is implemented using:

- Android Java for mobile application development
- XML layouts for UI design
- SQLite for local persistence
- Node.js backend for live data access
- REST APIs for backend communication
- Object-oriented design for maintainability and scalability

## 2. Problem Statement

Traditional railway booking systems require users to perform multiple operations such as route selection, journey planning, ticket booking, and management of travel records. Building a system of this type from scratch requires careful handling of:

- data modeling
- user interaction flow
- route and schedule retrieval
- database storage
- API security
- system modularity

RailReserve was developed to solve these concerns in a simplified form by providing a railway booking application that demonstrates how a real-world system can be modeled and implemented using modern software engineering practices.

## 3. Objectives

The main objectives of this project are:

1. To design and develop a railway booking system for Android.
2. To implement a modular software architecture using separate UI, service, model, DAO, and database components.
3. To store booking-related information locally using SQLite.
4. To integrate live train information through a backend service instead of exposing provider credentials in the Android app.
5. To demonstrate software engineering concepts such as encapsulation, abstraction, inheritance, polymorphism, and separation of concerns.
6. To create a project that is suitable for academic presentation and technical documentation.

## 4. Scope of the Project

The project scope includes:

- source and destination train search
- date-based journey selection
- train result listing
- train detail and schedule viewing
- fare and availability checks through live backend APIs
- local demo booking management
- PNR-like lookup and cancellation simulation
- offline database-based record keeping

The project is not intended to replace a real reservation system or connect to official government ticketing services. It is a demo platform built to illustrate software development principles and system design.

## 5. Functional Features

RailReserve includes the following features:

- Search trains between stations using source, destination, and date information
- View train results and route details
- Select travel class and other booking preferences
- Retrieve live station and schedule information through the backend
- Check fare and availability for a selected train when enabled
- Save booking information locally in SQLite
- Retrieve and manage previously booked records
- Simulate booking lookup and cancellation operations
- Provide a user-friendly Android interface using XML-based UI components

## 6. Software Engineering Aspects

This project demonstrates several key software engineering principles:

### 6.1 Layered Architecture
The project is organized into multiple layers so that responsibilities are clearly separated.

- Presentation Layer: screens and user interaction flows
- Model Layer: data classes and domain objects
- Service Layer: business logic and operations
- DAO Layer: database access logic
- Database Layer: SQLite management and schema handling
- Backend Layer: Express.js API and provider integration

### 6.2 Object-Oriented Design
The application uses object-oriented programming concepts such as:

- encapsulation through private model and service state
- abstraction through service interfaces and reusable logic
- inheritance where applicable in behavior-oriented classes
- polymorphism in service implementations and UI flows

### 6.3 Data Management
Local data is stored in SQLite to allow the user to maintain booking records and offline access without depending on the network for all operations.

### 6.4 Security Awareness
The Android app does not directly contain sensitive provider credentials. Instead, backend integration keeps secrets in a secure environment and exposes only the required API endpoints to the client.

### 6.5 Testing and Validation
The project includes Android unit tests and backend tests to validate logic, data contracts, and service behavior.

## 7. Technology Stack

The project uses the following technologies and tools:

- Java
- Android Studio
- XML Layouts
- SQLite Database
- Gradle
- Node.js
- Express.js
- REST APIs
- RailCore-compatible backend adapter
- Git and version control

## 8. System Architecture

The RailReserve architecture can be described as a client-server style model with local persistence.

### Android Client
The Android app provides the user interface and handles booking-related interactions. It communicates with the backend for live data and stores local records in SQLite.

### Backend Server
The Node.js backend acts as an intermediary between the Android app and the upstream RailCore provider. It validates requests, sanitizes responses, and protects sensitive credentials.

### Local Database
The SQLite database stores user booking information and related local app data. This makes the system functional even when live connectivity is unavailable.

## 9. Project Structure

```text
RailReserve/
├── app/
│   ├── src/
│   │   ├── main/java/com/railreserve/
│   │   ├── main/res/
│   │   ├── androidTest/
│   │   └── test/
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── backend/
│   ├── src/
│   ├── test/
│   ├── scripts/
│   ├── package.json
│   ├── .env.example
│   └── README.md
├── docs/
│   ├── architecture/
│   ├── uml/
│   └── live-mode-setup.md
├── gradlew
├── gradlew.bat
├── build.gradle.kts
├── settings.gradle.kts
├── README.md
├── AGENTS.md
└── .gitignore
```

## 10. Module Description

### Android Application Modules
- MainActivity: entry screen for route search and navigation
- LiveSearchActivity: allows users to search live station and journey information
- TrainResultsActivity: displays available trains for a selected route
- TrainDetailsActivity: presents train-related information and schedule
- BookingActivity: handles booking flow and confirmation UI
- MyBookingsActivity: shows saved bookings by the user
- FindBookingActivity: supports lookup of existing bookings

### Service and Data Modules
- TrainSearchService: handles search and route logic
- BookingService: manages booking operations
- DatabaseHelper: initializes and manages SQLite database schema
- SQLiteBookingDao: handles booking records in the database
- BackendApiClient: fetches live data from the backend server

## 11. Usage Workflow

A typical user flow in the project is as follows:

1. User opens the Android app.
2. User selects source and destination stations.
3. User chooses travel date and class.
4. The app searches available trains.
5. User views route and train details.
6. The user can check live data or use the local demo mode.
7. A booking is created and stored in SQLite.
8. The user can view, search, and manage bookings later.

## 12. Setup and Run Instructions

### Prerequisites

- Android Studio installed
- Android SDK Platform 37 or compatible version
- JDK 17 or later
- Node.js 24.5 or later
- npm
- At least one RailCore API key for live backend access; two optional keys can be configured as HTTP 429 fallbacks

### Android Build

From the project root, run:

```powershell
.\gradlew.bat :app:assembleDebug
```

The APK will be generated in:

```text
app\build\outputs\apk\debug\app-debug.apk
```

### Backend Setup

```powershell
Set-Location .\backend
Copy-Item .env.example .env
npm install
npm start
```

The backend starts on port 3000 and exposes APIs used by the Android client.
Set `RAILCORE_API_KEY` in `backend\.env`. Optionally set `RAILCORE_API_KEY_2` and `RAILCORE_API_KEY_3` for fallback. On HTTP 429 from RailCore, the backend retries the request using the next configured key; it does not switch keys for other errors. Keep all keys in the private backend environment and never commit them.

## 13. Testing

The project includes testing for both the application and backend.

### Android Tests

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

### Backend Tests

```powershell
Set-Location .\backend
npm test
npm run lint
```

These tests validate core logic and ensure the project remains stable as features evolve.

## 14. Limitations

This is a demo application and is not a live commercial reservation platform. The system intentionally avoids real payment processing and official ticket issuance. It demonstrates software engineering principles and application integration rather than creating a production-grade railway booking system.

## 15. Future Enhancements

The project can be extended in the future with:

- user authentication and profile management
- seat selection with real-time booking states
- ticket PDF generation
- payment gateway integration
- admin dashboard for train management
- multi-language support
- improved analytics and reporting

## 16. Conclusion

RailReserve is a complete software engineering project that demonstrates how a railway booking system can be designed and implemented using Android development, SQLite persistence, backend integration, and layered architecture. The project emphasizes clean system design, modular software development, secure API access, and practical user-focused functionality.

It is suitable for academic evaluation, project documentation, and technical demonstration because it covers major software engineering concepts from design to implementation and testing.

## 17. Related Documentation

- [backend/README.md](backend/README.md)
- [docs/live-mode-setup.md](docs/live-mode-setup.md)
- [docs/architecture/PHASE1_CONTRACTS.md](docs/architecture/PHASE1_CONTRACTS.md)
- [docs/architecture/PHASE2_CONTRACTS.md](docs/architecture/PHASE2_CONTRACTS.md)
