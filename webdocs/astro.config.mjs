// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

// When deploying to GitHub Pages the site is served from a project path
// (https://<owner>.github.io/<repo>/), so `base` must be set. Locally we keep
// the root so `npm run dev` serves at http://localhost:4321/.
const onGitHubPages = process.env.GITHUB_PAGES === 'true';

// https://astro.build/config
export default defineConfig({
	site: onGitHubPages ? 'https://dev.aldrete.github.io' : 'http://localhost:4321',
	base: onGitHubPages ? '/sysport-marjan' : undefined,
	integrations: [
		starlight({
			title: 'SysPort MARJAN',
			description:
				'Internal developer wiki for the SysPort-MARJAN transport management system: architecture, database, business rules and feature guides.',
			logo: { src: './src/assets/houston.webp', replacesTitle: false },
			social: [
				{
					icon: 'github',
					label: 'GitHub',
					href: 'https://github.com/DevAldrete/sysport-marjan',
				},
			],
			editLink: {
				baseUrl: 'https://github.com/DevAldrete/sysport-marjan/edit/main/webdocs/',
			},
			sidebar: [
				{
					label: 'Start here',
					items: [
						{ label: 'Overview', slug: 'start/overview' },
						{ label: 'Local setup', slug: 'start/setup' },
						{ label: 'Your first change', slug: 'start/first-change' },
						{ label: 'Testing', slug: 'start/testing' },
					],
				},
				{
					label: 'Architecture',
					items: [
						{ label: 'Architecture overview', slug: 'architecture/overview' },
						{ label: 'The four layers', slug: 'architecture/layers' },
						{ label: 'Database access', slug: 'architecture/database-access' },
						{ label: 'UI toolkit', slug: 'architecture/ui-toolkit' },
						{ label: 'Conventions', slug: 'architecture/conventions' },
					],
				},
				{
					label: 'Database',
					items: [
						{ label: 'Schema', slug: 'database/schema' },
						{ label: 'Functions', slug: 'database/functions' },
						{ label: 'Stored procedures', slug: 'database/procedures' },
						{ label: 'Views', slug: 'database/views' },
						{ label: 'Seed data & roles', slug: 'database/seed' },
						{ label: 'Schema changes', slug: 'database/migrations' },
					],
				},
				{
					label: 'Domain & rules',
					items: [
						{ label: 'Lifecycles', slug: 'domain/lifecycle' },
						{ label: 'Business rules', slug: 'domain/business-rules' },
						{ label: 'Assignment', slug: 'domain/assignment' },
						{ label: 'Costs & advances', slug: 'domain/costs' },
						{ label: 'Invoicing & payments', slug: 'domain/invoicing' },
					],
				},
				{
					label: 'Feature guides',
					items: [
						{ label: 'Security & users', slug: 'features/security' },
						{ label: 'Clients & rates', slug: 'features/clients' },
						{ label: 'Service requests', slug: 'features/requests' },
						{ label: 'Trips', slug: 'features/trips' },
						{ label: 'Fleet', slug: 'features/fleet' },
						{ label: 'Operators', slug: 'features/operators' },
						{ label: 'Finance', slug: 'features/finance' },
						{ label: 'Reports', slug: 'features/reports' },
					],
				},
				{
					label: 'Reference',
					items: [
						{ label: 'Java class map', slug: 'reference/java-map' },
						{ label: 'Glossary', slug: 'reference/glossary' },
						{ label: 'Troubleshooting', slug: 'reference/troubleshooting' },
					],
				},
			],
		}),
	],
});
