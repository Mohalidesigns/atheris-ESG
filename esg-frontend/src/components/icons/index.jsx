/**
 * D49 (amended 24 Nov 2026): the app's one icon set is Heroicons v2 outline (24 px grid, 1.5 stroke), as in Thirdline.
 * Each icon is a MUI SvgIcon drawing a Heroicons glyph, so `fontSize`, `color`, `sx` and `titleAccess` work as they did
 * with @mui/icons-material, and MUI slots (Chip icon, InputAdornment, IconButton) treat them as icons.
 *
 * Names keep the @mui/icons-material ones the app already used, so call sites didn't change; Thirdline-only names
 * (Squares2X2, Cog6Tooth, ChevronDoubleLeft…) are exported under their Heroicons names. Import icons from here only
 * (ESLint forbids @mui/icons-material).
 */
/* eslint-disable react-refresh/only-export-components -- every export is an icon made by hero(); fast refresh isn't needed here */
import {
  AdjustmentsHorizontalIcon, ArrowDownIcon, ArrowDownTrayIcon, ArrowLeftIcon, ArrowPathIcon,
  ArrowRightStartOnRectangleIcon, ArrowUpIcon, Bars3Icon, BellAlertIcon, BellIcon, CalculatorIcon, CameraIcon,
  ChartPieIcon, CheckBadgeIcon, CheckIcon, ChevronDoubleLeftIcon, ChevronDoubleRightIcon, ChevronDownIcon,
  ChevronLeftIcon, ChevronRightIcon, ChevronUpDownIcon, ClipboardDocumentListIcon, ClockIcon, CloudArrowUpIcon,
  CodeBracketIcon, Cog6ToothIcon, CurrencyDollarIcon, DevicePhoneMobileIcon, DocumentArrowUpIcon,
  DocumentDuplicateIcon, DocumentMagnifyingGlassIcon, DocumentTextIcon, ExclamationTriangleIcon, GlobeAltIcon,
  GlobeEuropeAfricaIcon, KeyIcon, ListBulletIcon, LockOpenIcon, MagnifyingGlassIcon, MapPinIcon, MinusIcon,
  NoSymbolIcon, PaperAirplaneIcon, PaperClipIcon, PencilSquareIcon, PlusIcon, PresentationChartLineIcon,
  ShieldCheckIcon, SignalSlashIcon, SparklesIcon, Squares2X2Icon, StarIcon, TrashIcon, UserCircleIcon, UserGroupIcon,
  UserPlusIcon, UsersIcon, XMarkIcon,
} from '@heroicons/react/24/outline';
import { StarIcon as StarSolidIcon } from '@heroicons/react/24/solid';
import { hero, SOLID } from './hero';
// The icons the first page load needs (sign-in, the menu) live in ./shell, so this full set stays out of it.
export * from './shell';

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
export const ChevronDoubleLeft = hero(ChevronDoubleLeftIcon, 'ChevronDoubleLeft');
export const ChevronDoubleRight = hero(ChevronDoubleRightIcon, 'ChevronDoubleRight');
export const ChevronUpDown = hero(ChevronUpDownIcon, 'ChevronUpDown');
export const ClipboardDocumentList = hero(ClipboardDocumentListIcon, 'ClipboardDocumentList');
export const Clock = hero(ClockIcon, 'Clock');
export const ChartPie = hero(ChartPieIcon, 'ChartPie');
export const CurrencyDollar = hero(CurrencyDollarIcon, 'CurrencyDollar');
export const GlobeEuropeAfrica = hero(GlobeEuropeAfricaIcon, 'GlobeEuropeAfrica');

/* Names the app imported from @mui/icons-material, drawn with their Heroicons counterpart */
export const Add = hero(PlusIcon, 'Add');
export const AdminPanelSettingsOutlined = hero(Cog6ToothIcon, 'AdminPanelSettingsOutlined');
export const ArrowBack = hero(ArrowLeftIcon, 'ArrowBack');
export const ArrowDownward = hero(ArrowDownIcon, 'ArrowDownward');
export const ArrowUpward = hero(ArrowUpIcon, 'ArrowUpward');
export const AttachFile = hero(PaperClipIcon, 'AttachFile');
export const AutoAwesome = hero(SparklesIcon, 'AutoAwesome');
export const AutoGraph = hero(PresentationChartLineIcon, 'AutoGraph');
export const Block = hero(NoSymbolIcon, 'Block');
export const Calculate = hero(CalculatorIcon, 'Calculate');
export const Check = hero(CheckIcon, 'Check');
export const ChevronLeft = hero(ChevronLeftIcon, 'ChevronLeft');
export const ChevronRight = hero(ChevronRightIcon, 'ChevronRight');
export const Close = hero(XMarkIcon, 'Close');
export const CloudOff = hero(SignalSlashIcon, 'CloudOff');
export const CloudSync = hero(ArrowPathIcon, 'CloudSync');
export const CloudUpload = hero(CloudArrowUpIcon, 'CloudUpload');
export const ContentCopy = hero(DocumentDuplicateIcon, 'ContentCopy');
export const DataObject = hero(CodeBracketIcon, 'DataObject');
export const Delete = hero(TrashIcon, 'Delete');
export const DeleteOutline = hero(TrashIcon, 'DeleteOutline');
export const Description = hero(DocumentTextIcon, 'Description');
export const Download = hero(ArrowDownTrayIcon, 'Download');
export const Edit = hero(PencilSquareIcon, 'Edit');
export const FormatListBulleted = hero(ListBulletIcon, 'FormatListBulleted');
export const GridViewOutlined = hero(Squares2X2Icon, 'GridViewOutlined');
export const GroupAdd = hero(UserGroupIcon, 'GroupAdd');
export const Key = hero(KeyIcon, 'Key');
export const KeyboardArrowDown = hero(ChevronDownIcon, 'KeyboardArrowDown');
export const KeyboardArrowRight = hero(ChevronRightIcon, 'KeyboardArrowRight');
export const LockOpen = hero(LockOpenIcon, 'LockOpen');
export const LogoutOutlined = hero(ArrowRightStartOnRectangleIcon, 'LogoutOutlined');
export const ManageAccounts = hero(UserCircleIcon, 'ManageAccounts');
export const ManageSearch = hero(DocumentMagnifyingGlassIcon, 'ManageSearch');
export const Menu = hero(Bars3Icon, 'Menu');
export const MyLocation = hero(MapPinIcon, 'MyLocation');
export const NotificationsActive = hero(BellAlertIcon, 'NotificationsActive');
export const NotificationsOutlined = hero(BellIcon, 'NotificationsOutlined');
export const People = hero(UsersIcon, 'People');
export const PersonAdd = hero(UserPlusIcon, 'PersonAdd');
export const PhonelinkErase = hero(DevicePhoneMobileIcon, 'PhonelinkErase');
export const PhotoCamera = hero(CameraIcon, 'PhotoCamera');
export const RadioButtonUnchecked = hero(EmptyCircleGlyph, 'RadioButtonUnchecked');
export const Refresh = hero(ArrowPathIcon, 'Refresh');
export const Remove = hero(MinusIcon, 'Remove');
export const SatelliteAlt = hero(GlobeAltIcon, 'SatelliteAlt');
export const Search = hero(MagnifyingGlassIcon, 'Search');
export const Send = hero(PaperAirplaneIcon, 'Send');
export const Shield = hero(ShieldCheckIcon, 'Shield');
export const ShieldOutlined = hero(ShieldCheckIcon, 'ShieldOutlined');
export const Star = hero(StarSolidIcon, 'Star', SOLID);
export const StarBorder = hero(StarIcon, 'StarBorder');
export const Sync = hero(ArrowPathIcon, 'Sync');
export const Tune = hero(AdjustmentsHorizontalIcon, 'Tune');
export const UploadFile = hero(DocumentArrowUpIcon, 'UploadFile');
export const Verified = hero(CheckBadgeIcon, 'Verified');
export const VerifiedOutlined = hero(CheckBadgeIcon, 'VerifiedOutlined');
export const ViewModule = hero(Squares2X2Icon, 'ViewModule');
export const Warning = hero(ExclamationTriangleIcon, 'Warning');
