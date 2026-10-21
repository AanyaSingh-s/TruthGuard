# TruthGuard Frontend Development Dockerfile
FROM node:20-slim

WORKDIR /app

# Cache package dependencies
COPY package*.json ./
RUN npm install

# Copy application source code
COPY . .

EXPOSE 3000

ENV VITE_BACKEND_URL=http://localhost:8080
ENV VITE_AI_SERVICE_URL=http://localhost:8000

HEALTHCHECK --interval=10s --timeout=5s --start-period=15s --retries=5 \
  CMD node -e "require('http').get('http://localhost:3000', (r) => process.exit(r.statusCode === 200 ? 0 : 1))"

CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0", "--port", "3000"]
