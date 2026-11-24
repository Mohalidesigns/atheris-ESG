/**
 * D47 (amended 24 Nov 2026): the app's one icon set is Heroicons v2 outline (24 px grid, 1.5 stroke), as in Thirdline.
 * Each icon is a MUI SvgIcon drawing a Heroicons glyph, so `fontSize`, `color`, `sx` and `titleAccess` work as they did
 * with @mui/icons-material, and MUI slots (Chip icon, InputAdornment, IconButton) treat them as icons.
 *
 * Names keep the @mui/icons-material ones the app already used, so call sites didn't change; Thirdline-only names
 * (Squares2X2, Cog6Tooth, ChevronDoubleLeft…) are exported under their Heroicons names. Import icons from here only
 * (ESLint forbids @mui/icons-material).
 */
/* eslint-disable react-refresh/only-export-components -- every export is an icon made by hero(); fast refresh isn't needed here */
import { forwardRef } from 'react';
import SvgIcon from '@mui/material/SvgIcon';
import {
  AcademicCapIcon, AdjustmentsHorizontalIcon, ArrowDownIcon, ArrowDownTrayIcon, ArrowLeftIcon, ArrowPathIcon,
  ArrowRightStartOnRectangleIcon, ArrowTrendingUpIcon, ArrowUpIcon, Bars3Icon, BellAlertIcon, BellIcon, BuildingLibraryIcon,
  BuildingOffice2Icon, BuildingStorefrontIcon, CalculatorIcon, CameraIcon, ChartBarIcon, ChartPieIcon, CheckBadgeIcon,
  CheckCircleIcon, CheckIcon, ChevronDoubleLeftIcon, ChevronDoubleRightIcon, ChevronDownIcon, ChevronLeftIcon,
  ChevronRightIcon, ChevronUpDownIcon, CircleStackIcon, ClipboardDocumentListIcon, ClockIcon, CloudArrowUpIcon,
  CodeBracketIcon, Cog6ToothIcon, CurrencyDollarIcon, DevicePhoneMobileIcon, DocumentArrowUpIcon, DocumentDuplicateIcon,
  DocumentMagnifyingGlassIcon, DocumentTextIcon, EnvelopeIcon, ExclamationCircleIcon, ExclamationTriangleIcon, EyeIcon, EyeSlashIcon,
  GlobeAltIcon, GlobeEuropeAfricaIcon, InformationCircleIcon, KeyIcon, LanguageIcon, ListBulletIcon, LockClosedIcon,
  LockOpenIcon, MagnifyingGlassIcon, MapPinIcon, MinusIcon, NoSymbolIcon, PaperAirplaneIcon, PaperClipIcon,
  PencilSquareIcon, PlusIcon, PresentationChartLineIcon, ScaleIcon, ShieldCheckIcon, SignalSlashIcon, SparklesIcon,
  Squares2X2Icon, StarIcon, TrashIcon, TruckIcon, UserCircleIcon, UserGroupIcon, UserPlusIcon, UsersIcon,
  WrenchScrewdriverIcon, XMarkIcon,
} from '@heroicons/react/24/outline';
import { StarIcon as StarSolidIcon } from '@heroicons/react/24/solid';

// Outline glyphs are stroked; MUI's SvgIcon fills with currentColor, which would flood them.
const OUTLINE = { fill: 'none' };
const SOLID = { fill: 'currentColor' };

function hero(Glyph, name, style = OUTLINE) {
  const Icon = forwardRef(function Icon({ sx, titleAccess, ...props }, ref) {
    // Heroicons draw their own <title> from `title` (they drop MUI's child <title>); SvgIcon still sets role="img".
    return (
      <SvgIcon ref={ref} component={Glyph} data-testid={`${name}Icon`} titleAccess={titleAccess} title={titleAccess} {...props}
        sx={[style, ...(Array.isArray(sx) ? sx : [sx])]} />
    );
  });
  Icon.displayName = `${name}Icon`;
  Icon.muiName = 'SvgIcon';
  return Icon;
}

// Heroicons has no empty circle; drawn on the same 24 px grid and 1.5 stroke.
function EmptyCircleGlyph({ title, ...props }) {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" aria-hidden="true" {...props}>
      {title ? <title>{title}</title> : null}
      <circle cx="12" cy="12" r="9" />
    </svg>
  );
}

/* Thirdline / Heroicons names */
export const Squares2X2 = hero(Squares2X2Icon, 'Squares2X2');
export const Cog6Tooth = hero(Cog6ToothIcon, 'Cog6Tooth');
export const ChevronDoubleLeft = hero(ChevronDoubleLeftIcon, 'ChevronDoubleLeft');
export const ChevronDoubleRight = hero(ChevronDoubleRightIcon, 'ChevronDoubleRight');
export const ChevronDown = hero(ChevronDownIcon, 'ChevronDown');
export const ChevronUpDown = hero(ChevronUpDownIcon, 'ChevronUpDown');
export const ClipboardDocumentList = hero(ClipboardDocumentListIcon, 'ClipboardDocumentList');
export const Clock = hero(ClockIcon, 'Clock');
export const ChartPie = hero(ChartPieIcon, 'ChartPie');
export const CurrencyDollar = hero(CurrencyDollarIcon, 'CurrencyDollar');
export const GlobeEuropeAfrica = hero(GlobeEuropeAfricaIcon, 'GlobeEuropeAfrica');
export const XMark = hero(XMarkIcon, 'XMark');

/* Names the app imported from @mui/icons-material, drawn with their Heroicons counterpart */
export const AccountBalanceOutlined = hero(BuildingLibraryIcon, 'AccountBalanceOutlined');
export const Add = hero(PlusIcon, 'Add');
export const AdminPanelSettingsOutlined = hero(Cog6ToothIcon, 'AdminPanelSettingsOutlined');
export const ArrowBack = hero(ArrowLeftIcon, 'ArrowBack');
export const ArrowDownward = hero(ArrowDownIcon, 'ArrowDownward');
export const ArrowUpward = hero(ArrowUpIcon, 'ArrowUpward');
export const AttachFile = hero(PaperClipIcon, 'AttachFile');
export const AutoAwesome = hero(SparklesIcon, 'AutoAwesome');
export const AutoGraph = hero(PresentationChartLineIcon, 'AutoGraph');
export const BarChartOutlined = hero(ChartBarIcon, 'BarChartOutlined');
export const Block = hero(NoSymbolIcon, 'Block');
export const Calculate = hero(CalculatorIcon, 'Calculate');
export const Check = hero(CheckIcon, 'Check');
export const CheckCircle = hero(CheckCircleIcon, 'CheckCircle');
export const ChevronLeft = hero(ChevronLeftIcon, 'ChevronLeft');
export const ChevronRight = hero(ChevronRightIcon, 'ChevronRight');
export const Close = hero(XMarkIcon, 'Close');
export const CloudOff = hero(SignalSlashIcon, 'CloudOff');
export const CloudSync = hero(ArrowPathIcon, 'CloudSync');
export const CloudUpload = hero(CloudArrowUpIcon, 'CloudUpload');
export const Construction = hero(WrenchScrewdriverIcon, 'Construction');
export const ContentCopy = hero(DocumentDuplicateIcon, 'ContentCopy');
export const DataObject = hero(CodeBracketIcon, 'DataObject');
export const Delete = hero(TrashIcon, 'Delete');
export const DeleteOutline = hero(TrashIcon, 'DeleteOutline');
export const Description = hero(DocumentTextIcon, 'Description');
export const DescriptionOutlined = hero(DocumentTextIcon, 'DescriptionOutlined');
export const Download = hero(ArrowDownTrayIcon, 'Download');
export const Edit = hero(PencilSquareIcon, 'Edit');
export const Email = hero(EnvelopeIcon, 'Email');
export const ErrorOutline = hero(ExclamationCircleIcon, 'ErrorOutline');
export const FactoryOutlined = hero(BuildingOffice2Icon, 'FactoryOutlined');
export const FormatListBulleted = hero(ListBulletIcon, 'FormatListBulleted');
export const GavelOutlined = hero(ScaleIcon, 'GavelOutlined');
export const GridViewOutlined = hero(Squares2X2Icon, 'GridViewOutlined');
export const GroupAdd = hero(UserGroupIcon, 'GroupAdd');
export const InfoOutlined = hero(InformationCircleIcon, 'InfoOutlined');
export const Key = hero(KeyIcon, 'Key');
export const KeyboardArrowDown = hero(ChevronDownIcon, 'KeyboardArrowDown');
export const KeyboardArrowRight = hero(ChevronRightIcon, 'KeyboardArrowRight');
export const LocalShippingOutlined = hero(TruckIcon, 'LocalShippingOutlined');
export const Lock = hero(LockClosedIcon, 'Lock');
export const LockOpen = hero(LockOpenIcon, 'LockOpen');
export const LogoutOutlined = hero(ArrowRightStartOnRectangleIcon, 'LogoutOutlined');
export const ManageAccounts = hero(UserCircleIcon, 'ManageAccounts');
export const ManageSearch = hero(DocumentMagnifyingGlassIcon, 'ManageSearch');
export const Menu = hero(Bars3Icon, 'Menu');
export const MyLocation = hero(MapPinIcon, 'MyLocation');
export const NotificationsActive = hero(BellAlertIcon, 'NotificationsActive');
export const NotificationsOutlined = hero(BellIcon, 'NotificationsOutlined');
export const People = hero(UsersIcon, 'People');
export const PeopleOutline = hero(UsersIcon, 'PeopleOutline');
export const PersonAdd = hero(UserPlusIcon, 'PersonAdd');
export const PhonelinkErase = hero(DevicePhoneMobileIcon, 'PhonelinkErase');
export const PhotoCamera = hero(CameraIcon, 'PhotoCamera');
export const RadioButtonUnchecked = hero(EmptyCircleGlyph, 'RadioButtonUnchecked');
export const Refresh = hero(ArrowPathIcon, 'Refresh');
export const Remove = hero(MinusIcon, 'Remove');
export const SatelliteAlt = hero(GlobeAltIcon, 'SatelliteAlt');
export const SchoolOutlined = hero(AcademicCapIcon, 'SchoolOutlined');
export const Search = hero(MagnifyingGlassIcon, 'Search');
export const Send = hero(PaperAirplaneIcon, 'Send');
export const Shield = hero(ShieldCheckIcon, 'Shield');
export const ShieldOutlined = hero(ShieldCheckIcon, 'ShieldOutlined');
export const Star = hero(StarSolidIcon, 'Star', SOLID);
export const StarBorder = hero(StarIcon, 'StarBorder');
export const StorageOutlined = hero(CircleStackIcon, 'StorageOutlined');
export const StorefrontOutlined = hero(BuildingStorefrontIcon, 'StorefrontOutlined');
export const Sync = hero(ArrowPathIcon, 'Sync');
export const Translate = hero(LanguageIcon, 'Translate');
export const TrendingUpOutlined = hero(ArrowTrendingUpIcon, 'TrendingUpOutlined');
export const Tune = hero(AdjustmentsHorizontalIcon, 'Tune');
export const UploadFile = hero(DocumentArrowUpIcon, 'UploadFile');
export const Verified = hero(CheckBadgeIcon, 'Verified');
export const VerifiedOutlined = hero(CheckBadgeIcon, 'VerifiedOutlined');
export const ViewModule = hero(Squares2X2Icon, 'ViewModule');
export const Visibility = hero(EyeIcon, 'Visibility');
export const VisibilityOff = hero(EyeSlashIcon, 'VisibilityOff');
export const Warning = hero(ExclamationTriangleIcon, 'Warning');
export const WarningAmber = hero(ExclamationTriangleIcon, 'WarningAmber');
export const WarningAmberOutlined = hero(ExclamationTriangleIcon, 'WarningAmberOutlined');
