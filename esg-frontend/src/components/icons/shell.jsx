/**
 * D49: the icons on the first page load (sign-in form, language picker, empty states, confirm dialog, the theme's MUI glyphs, the menu). Kept apart from
 * ./index.jsx so the sign-in page doesn't download every icon (WP-53 bundle budget). Import from '../icons' as usual:
 * index.jsx re-exports these.
 */
import {
  AcademicCapIcon, ArrowTrendingUpIcon, BuildingLibraryIcon, BuildingOffice2Icon, BuildingStorefrontIcon,
  ChartBarIcon, CheckCircleIcon, ChevronDownIcon, CircleStackIcon, Cog6ToothIcon, DocumentTextIcon, EnvelopeIcon,
  ExclamationCircleIcon, ExclamationTriangleIcon, EyeIcon, EyeSlashIcon, InformationCircleIcon, LanguageIcon,
  LockClosedIcon, ScaleIcon, Squares2X2Icon, TruckIcon, UsersIcon, WrenchScrewdriverIcon, XMarkIcon,
} from '@heroicons/react/24/outline';
import { hero } from './hero';

export const Squares2X2 = hero(Squares2X2Icon, 'Squares2X2');
export const Cog6Tooth = hero(Cog6ToothIcon, 'Cog6Tooth');
export const AccountBalanceOutlined = hero(BuildingLibraryIcon, 'AccountBalanceOutlined');
export const BarChartOutlined = hero(ChartBarIcon, 'BarChartOutlined');
export const Construction = hero(WrenchScrewdriverIcon, 'Construction');
export const DescriptionOutlined = hero(DocumentTextIcon, 'DescriptionOutlined');
export const Email = hero(EnvelopeIcon, 'Email');
export const FactoryOutlined = hero(BuildingOffice2Icon, 'FactoryOutlined');
export const GavelOutlined = hero(ScaleIcon, 'GavelOutlined');
export const LocalShippingOutlined = hero(TruckIcon, 'LocalShippingOutlined');
export const Lock = hero(LockClosedIcon, 'Lock');
export const PeopleOutline = hero(UsersIcon, 'PeopleOutline');
export const SchoolOutlined = hero(AcademicCapIcon, 'SchoolOutlined');
export const StorageOutlined = hero(CircleStackIcon, 'StorageOutlined');
export const StorefrontOutlined = hero(BuildingStorefrontIcon, 'StorefrontOutlined');
export const Translate = hero(LanguageIcon, 'Translate');
export const TrendingUpOutlined = hero(ArrowTrendingUpIcon, 'TrendingUpOutlined');
export const Visibility = hero(EyeIcon, 'Visibility');
export const VisibilityOff = hero(EyeSlashIcon, 'VisibilityOff');
export const WarningAmberOutlined = hero(ExclamationTriangleIcon, 'WarningAmberOutlined');
export const WarningAmber = hero(ExclamationTriangleIcon, 'WarningAmber');
export const CheckCircle = hero(CheckCircleIcon, 'CheckCircle');
export const ChevronDown = hero(ChevronDownIcon, 'ChevronDown');
export const ErrorOutline = hero(ExclamationCircleIcon, 'ErrorOutline');
export const InfoOutlined = hero(InformationCircleIcon, 'InfoOutlined');
export const XMark = hero(XMarkIcon, 'XMark');
